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

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.gsginzburg.cluster.framework.security.DispatchValidationClient;
import org.gsginzburg.cluster.framework.security.ValidatedToken;
import org.gsginzburg.cluster.sample.AbstractClusterSampleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class ContextEndpointIntegrationTest extends AbstractClusterSampleIntegrationTest {

    @InjectMock DispatchValidationClient validationClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(validationClient);
    }

    @Test
    void getContext_withValidToken_returns200WithResolvedData() {
        String tenantId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String token = "test-token-" + UUID.randomUUID();

        Mockito.when(validationClient.validate(token)).thenReturn(new ValidatedToken(
                userId, "test@example.com", "Test", "User", "TENANT",
                tenantId, "Test Tenant", "ACTIVE",
                "test-cluster", "http://localhost:8081",
                List.of("USER")
        ));

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/context")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.localContext", notNullValue())
            .body("data.user.email", is("test@example.com"))
            .body("data.tenant.name", is("Test Tenant"));
    }

    @Test
    void getContext_withBackofficeToken_returns200() {
        String userId = UUID.randomUUID().toString();
        String token = "backoffice-token-" + UUID.randomUUID();

        Mockito.when(validationClient.validate(token)).thenReturn(new ValidatedToken(
                userId, "admin@dispatch.local", "Admin", "User", "BACKOFFICE",
                null, null, null, null, null,
                List.of("BACKOFFICE")
        ));

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/context")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.user.email", is("admin@dispatch.local"));
    }

    @Test
    void getContext_withNoAuthHeader_returns401() {
        given()
            .get("/api/app/context")
        .then()
            .statusCode(401);
    }

    @Test
    void getContext_whenValidationFails_returns401() {
        String token = "invalid-token-" + UUID.randomUUID();
        Mockito.when(validationClient.validate(token)).thenReturn(null);

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/context")
        .then()
            .statusCode(401);
    }
}
