/*
 * Copyright 2026 Gary Ginzburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsginzburg.cluster.sample.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Playwright test for the backoffice-to-tenant authentication flow.
 *
 * Flow:
 *  1. POST /api/auth/login           → backoffice JWT (dispatch-service)
 *  2. POST /api/auth/scope-to-tenant → tenant-scoped JWT (dispatch-service)
 *  3. GET  /api/app/context          → tenant context (cluster-sample)
 *  4. GET  /api/app/test-records     → list from tenant schema (cluster-sample)
 *
 * Requires both services running at their default ports (8080 / 8081).
 */
class BackofficeTenantContextE2ETest {

    private static final String DISPATCH_URL = "http://127.0.0.1:8080";
    private static final String CLUSTER_URL  = "http://127.0.0.1:8081";
    private static final String BO_EMAIL     = "admin@dispatch.local";
    private static final String BO_PASSWORD  = "Admin@1234";
    private static final String TENANT_ID    = "fd779dc4-62bb-47c8-a89f-45b3919a28e7";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void backofficeLogin_scopeToTenant_clusterContextHasTenantId() throws Exception {
        assumeServicesRunning();

        try (Playwright playwright = Playwright.create()) {
            APIRequestContext dispatch = playwright.request().newContext(
                    new APIRequest.NewContextOptions().setBaseURL(DISPATCH_URL));

            // Step 1 — login as backoffice user
            APIResponse loginResp = dispatch.post("/api/auth/login",
                    RequestOptions.create().setData(Map.of("email", BO_EMAIL, "password", BO_PASSWORD)));

            assertThat(loginResp.status())
                    .as("dispatch login should return 200")
                    .isEqualTo(200);

            String backofficToken = field(loginResp, "data", "accessToken");

            // Step 2 — exchange backoffice token for a tenant-scoped JWT
            APIResponse scopeResp = dispatch.post("/api/auth/scope-to-tenant",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + backofficToken)
                            .setData(Map.of("tenantId", TENANT_ID)));

            assertThat(scopeResp.status())
                    .as("scope-to-tenant should return 200")
                    .isEqualTo(200);

            String scopedToken = field(scopeResp, "data", "scopedToken");

            // Step 3 — call cluster-sample API with the scoped JWT
            APIRequestContext cluster = playwright.request().newContext(
                    new APIRequest.NewContextOptions().setBaseURL(CLUSTER_URL));

            APIResponse ctxResp = cluster.get("/api/app/context",
                    RequestOptions.create().setHeader("Authorization", "Bearer " + scopedToken));

            assertThat(ctxResp.status())
                    .as("cluster-sample /api/app/context should return 200")
                    .isEqualTo(200);

            Map<String, Object> body = MAPPER.readValue(ctxResp.text(), new TypeReference<>() {});
            assertThat(body.get("success")).isEqualTo(true);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");

            @SuppressWarnings("unchecked")
            Map<String, Object> localContext = (Map<String, Object>) data.get("localContext");
            assertThat(localContext.get("schemaName"))
                    .as("localContext.schemaName must equal the tenant UUID")
                    .isEqualTo(TENANT_ID);
            assertThat((String) localContext.get("userId"))
                    .as("localContext.userId must be non-blank")
                    .isNotBlank();

            @SuppressWarnings("unchecked")
            Map<String, Object> tenant = (Map<String, Object>) data.get("tenant");
            assertThat(tenant.get("id"))
                    .as("tenant.id must match the scoped tenant")
                    .isEqualTo(TENANT_ID);

            // Step 4 — read test-records from the tenant schema
            APIResponse recordsResp = cluster.get("/api/app/test-records",
                    RequestOptions.create().setHeader("Authorization", "Bearer " + scopedToken));

            assertThat(recordsResp.status())
                    .as("/api/app/test-records should return 200")
                    .isEqualTo(200);

            Map<String, Object> recordsBody = MAPPER.readValue(recordsResp.text(), new TypeReference<>() {});
            assertThat(recordsBody.get("success"))
                    .as("test-records response must have success=true")
                    .isEqualTo(true);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) recordsBody.get("data");
            assertThat(records)
                    .as("test-records data must be a list")
                    .isNotNull()
                    .isInstanceOf(List.class);
        }
    }

    private String field(APIResponse response, String... path) throws Exception {
        Map<String, Object> node = MAPPER.readValue(response.text(), new TypeReference<>() {});
        for (int i = 0; i < path.length - 1; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) node.get(path[i]);
            node = next;
        }
        return (String) node.get(path[path.length - 1]);
    }

    private static void assumeServicesRunning() {
        for (int port : new int[]{8080, 8081}) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            } catch (Exception e) {
                Assumptions.abort("Service not listening on port " + port + " — skipping E2E test");
            }
        }
    }
}
