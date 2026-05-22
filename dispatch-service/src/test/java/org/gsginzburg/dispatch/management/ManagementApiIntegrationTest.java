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

package org.gsginzburg.dispatch.management;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.AbstractIntegrationTest;
import org.gsginzburg.dispatch.auth.jwt.DispatchJwtService;
import org.gsginzburg.dispatch.domain.model.AccessToken;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.ClusterStatus;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AccessTokenRepository;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.UserRole;
import org.gsginzburg.shared.security.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTest
public class ManagementApiIntegrationTest extends AbstractIntegrationTest {

    private static final String INTERNAL_API_KEY = "change-me-internal-api-key";
    private static final String WRONG_API_KEY    = "wrong-key";

    @Inject DispatchJwtService    jwtService;
    @Inject AccessTokenRepository accessTokenRepository;
    @Inject AppUserRepository     appUserRepository;
    @Inject ClusterRepository     clusterRepository;
    @Inject TenantRepository      tenantRepository;
    @Inject TenantUserRepository  tenantUserRepository;

    private AppUser backofficeUser;
    private AppUser tenantUser;
    private Cluster cluster;
    private Tenant  tenant;

    @Transactional
    public void setupTestData() {
        cluster = Cluster.builder()
                .name("mgmt-test-cluster")
                .url("http://localhost:8081")
                .status(ClusterStatus.ACTIVE)
                .build();
        clusterRepository.persist(cluster);

        tenant = Tenant.builder()
                .name("mgmt-test-tenant")
                .cluster(cluster)
                .status(TenantStatus.ACTIVE)
                .build();
        tenantRepository.persist(tenant);

        backofficeUser = AppUser.builder()
                .email("mgmt-bo@dispatch.local")
                .firstName("Mgmt").lastName("Admin")
                .userType(UserType.BACKOFFICE)
                .status(UserStatus.ACTIVE)
                .build();
        appUserRepository.persist(backofficeUser);

        tenantUser = AppUser.builder()
                .email("mgmt-tenant@dispatch.local")
                .firstName("Mgmt").lastName("Tenant")
                .userType(UserType.TENANT)
                .status(UserStatus.ACTIVE)
                .build();
        appUserRepository.persist(tenantUser);

        tenantUserRepository.persist(TenantUser.builder()
                .tenantId(tenant.getId())
                .userId(tenantUser.getId())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void cleanup() {
        DbProvisioner.runPsql("dispatch-test",
                "TRUNCATE dispatch.access_token, dispatch.refresh_token, dispatch.tenant_user, dispatch.tenant, dispatch.app_user, dispatch.cluster CASCADE");
    }

    @Test
    void validateToken_backofficeToken_returnsUserInfo() {
        setupTestData();
        String token = buildAndStoreToken(backofficeUser, null, List.of("BACKOFFICE"));

        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("token", token))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.user.email", is("mgmt-bo@dispatch.local"))
            .body("data.user.userType", is("BACKOFFICE"))
            .body("data.user.id", not(emptyOrNullString()));
    }

    @Test
    void validateToken_userToken_returnsUserAndTenantAndClusterInfo() {
        setupTestData();
        String token = buildAndStoreToken(tenantUser,
                tenant.getId().toString(), List.of("USER"));

        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("token", token))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.user.email", is("mgmt-tenant@dispatch.local"))
            .body("data.tenant.id", is(tenant.getId().toString()))
            .body("data.tenant.name", is("mgmt-test-tenant"))
            .body("data.cluster.id", is(cluster.getId().toString()))
            .body("data.cluster.url", is("http://localhost:8081"));
    }

    @Test
    void validateToken_noApiKey_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", "any-token"))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(401);
    }

    @Test
    void validateToken_wrongApiKey_returns401() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", WRONG_API_KEY)
            .body(Map.of("token", "any-token"))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(401);
    }

    @Test
    void validateToken_invalidJwt_returns401() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("token", "not.a.valid.jwt"))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(401);
    }

    @Test
    void validateToken_missingTokenField_returns400() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("other", "value"))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(400);
    }

    @Test
    void validateToken_expiredJwt_returns401() {
        setupTestData();
        Instant past = Instant.now().minusSeconds(3600);
        String token = jwtService.generateToken(JwtClaims.builder()
                .sub(backofficeUser.getId().toString())
                .roles(List.of("BACKOFFICE"))
                .issuedAt(past.minusSeconds(60))
                .expiresAt(past)
                .build());

        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("token", token))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(401);
    }

    @Test
    void validateToken_notStoredInDb_returns401() {
        setupTestData();
        String token = buildToken(backofficeUser, null, List.of("BACKOFFICE"));

        given()
            .contentType(ContentType.JSON)
            .header("X-Internal-Api-Key", INTERNAL_API_KEY)
            .body(Map.of("token", token))
            .post("/api/internal/token/validate")
        .then()
            .statusCode(401);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    public String buildAndStoreToken(AppUser user, String tenantId, List<String> roles) {
        String token = buildToken(user, tenantId, roles);
        Instant now = Instant.now();
        accessTokenRepository.persist(AccessToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(token))
                .expiresAt(OffsetDateTime.now().plusHours(8))
                .build());
        return token;
    }

    private String buildToken(AppUser user, String tenantId, List<String> roles) {
        Instant now = Instant.now();
        return jwtService.generateToken(JwtClaims.builder()
                .sub(user.getId().toString())
                .tenantId(tenantId)
                .roles(roles)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build());
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
