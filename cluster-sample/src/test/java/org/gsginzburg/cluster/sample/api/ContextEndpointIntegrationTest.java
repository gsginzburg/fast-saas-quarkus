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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import org.gsginzburg.cluster.sample.AbstractClusterSampleIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(ContextEndpointIntegrationTest.JwksTestResource.class)
public class ContextEndpointIntegrationTest extends AbstractClusterSampleIntegrationTest {

    public static class JwksTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer wireMock;
        static RSAKey testRsaKey;

        @Override
        public Map<String, String> start() {
            try {
                wireMock = new WireMockServer(wireMockConfig().dynamicPort());
                wireMock.start();

                testRsaKey = new RSAKeyGenerator(2048)
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyID("test-key-id")
                        .generate();

                String jwks = new JWKSet(testRsaKey.toPublicJWK()).toString();

                wireMock.stubFor(get(urlEqualTo("/api/auth/jwks"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(jwks)));

                return Map.of("cluster.dispatch-url", wireMock.baseUrl());
            } catch (Exception e) {
                throw new RuntimeException("Failed to start JWKS WireMock server", e);
            }
        }

        @Override
        public void stop() {
            if (wireMock != null) wireMock.stop();
        }
    }

    static String createToken(String userId, String email, String tenantId,
                              String tenantName, String userType, List<String> roles) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("dispatch")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("email", email)
                    .claim("user_type", userType)
                    .claim("roles", roles);
            if (tenantId   != null) builder.claim("tenant_id",   tenantId);
            if (tenantName != null) builder.claim("tenant_name", tenantName);

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID("test-key-id")
                            .build(),
                    builder.build());
            jwt.sign(new RSASSASigner(JwksTestResource.testRsaKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JWT", e);
        }
    }

    @Test
    void getContext_withValidTenantToken_returns200WithResolvedData() {
        String tenantId = UUID.randomUUID().toString();
        String userId   = UUID.randomUUID().toString();
        String token    = createToken(userId, "test@example.com", tenantId, "Test Tenant", "TENANT", List.of("USER"));

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/app/context")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.localContext", notNullValue())
            .body("data.user.email", is("test@example.com"));
    }

    @Test
    void getContext_withBackofficeToken_returns200() {
        String userId = UUID.randomUUID().toString();
        String token  = createToken(userId, "admin@dispatch.local", null, null, "BACKOFFICE", List.of("BACKOFFICE"));

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
    void getContext_withInvalidJwt_returns401() {
        given()
            .header("Authorization", "Bearer not.a.valid.jwt")
        .when()
            .get("/api/app/context")
        .then()
            .statusCode(401);
    }
}
