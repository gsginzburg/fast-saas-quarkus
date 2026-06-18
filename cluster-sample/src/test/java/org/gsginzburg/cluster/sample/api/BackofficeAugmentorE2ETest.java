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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.gsginzburg.cluster.sample.AbstractClusterSampleIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end proof that {@code BackofficeRoleAugmentor} is wired into the live auth pipeline.
 *
 * Boots the full cluster-sample app — the real {@code DispatchAuthMechanism} (which builds the
 * identity and now applies registered augmentors), CDI-discovered {@code BackofficeRoleAugmentor},
 * and JAX-RS {@code @RolesAllowed} enforcement. {@link AdminOnlyResource} is gated on {@code ADMIN}
 * only, a role a BACKOFFICE token does not carry — so reaching it confirms the augmentor ran.
 */
@QuarkusTest
@QuarkusTestResource(ContextEndpointIntegrationTest.JwksTestResource.class)
public class BackofficeAugmentorE2ETest extends AbstractClusterSampleIntegrationTest {

    @Test
    void backofficeToken_reachesAdminOnlyEndpoint_viaAugmentor() {
        String token = ContextEndpointIntegrationTest.createToken(
                UUID.randomUUID().toString(), "admin@dispatch.local",
                null, null, "BACKOFFICE", List.of("BACKOFFICE"));

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/admin-only")
        .then()
            .statusCode(200)
            .body("success", is(true))
            // Effective identity carries both the original and augmentor-added roles.
            .body("data", hasItems("ADMIN", "BACKOFFICE"));
    }

    @Test
    void userToken_isForbiddenFromAdminOnlyEndpoint() {
        // Control: a non-BACKOFFICE caller is not elevated, so ADMIN-only stays 403.
        String token = ContextEndpointIntegrationTest.createToken(
                UUID.randomUUID().toString(), "user@example.com",
                UUID.randomUUID().toString(), "Test Tenant", "TENANT", List.of("USER"));

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/admin-only")
        .then()
            .statusCode(403);
    }
}
