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

package org.gsginzburg.dispatch.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.AbstractIntegrationTest;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.ClusterStatus;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.security.UserRole;
import org.gsginzburg.shared.security.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
public class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String BO_EMAIL      = "bo-auth-test@dispatch.local";
    private static final String TENANT_EMAIL  = "tenant-auth-test@dispatch.local";
    private static final String PASSWORD      = "Test@Auth1234!";

    @Inject AppUserRepository   appUserRepository;
    @Inject ClusterRepository   clusterRepository;
    @Inject TenantRepository    tenantRepository;
    @Inject TenantUserRepository tenantUserRepository;

    @Transactional
    public void setupTestData() {
        Cluster cluster = Cluster.builder()
                .name("auth-test-cluster")
                .url("http://localhost:8081")
                .status(ClusterStatus.ACTIVE)
                .build();
        clusterRepository.persist(cluster);

        Tenant tenant = Tenant.builder()
                .name("auth-test-tenant")
                .cluster(cluster)
                .status(TenantStatus.ACTIVE)
                .build();
        tenantRepository.persist(tenant);

        AppUser boUser = AppUser.builder()
                .email(BO_EMAIL)
                .passwordHash(BcryptUtil.bcryptHash(PASSWORD))
                .firstName("Auth").lastName("Admin")
                .userType(UserType.BACKOFFICE)
                .status(UserStatus.ACTIVE)
                .build();
        appUserRepository.persist(boUser);

        AppUser tenantUser = AppUser.builder()
                .email(TENANT_EMAIL)
                .passwordHash(BcryptUtil.bcryptHash(PASSWORD))
                .firstName("Auth").lastName("Tenant")
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
    void login_backofficeCredentials_returns200WithBackofficeToken() {
        setupTestData();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL, "password", PASSWORD))
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.accessToken", not(emptyOrNullString()))
            .body("data.tokenType", is("Bearer"))
            .body("data.userType", is("BACKOFFICE"))
            .body("data.clusterUrl", nullValue())
            .body("data.email", is(BO_EMAIL))
            .body("data.refreshToken", not(emptyOrNullString()));
    }

    @Test
    void login_tenantCredentials_returns200WithExchangeTokenAndClusterUrl() {
        setupTestData();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", TENANT_EMAIL, "password", PASSWORD))
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.accessToken", not(emptyOrNullString()))
            .body("data.userType", is("TENANT"))
            .body("data.clusterUrl", is("http://localhost:8081"))
            .body("data.email", is(TENANT_EMAIL))
            .body("data.refreshToken", not(emptyOrNullString()));
    }

    @Test
    void login_unknownEmail_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "nobody@dispatch.local", "password", PASSWORD))
            .post("/api/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void login_wrongPassword_returns401() {
        setupTestData();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL, "password", "WrongPass!999"))
            .post("/api/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void login_missingEmailField_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("password", PASSWORD))
            .post("/api/auth/login")
        .then()
            .statusCode(400);
    }

    @Test
    void login_missingPasswordField_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL))
            .post("/api/auth/login")
        .then()
            .statusCode(400);
    }

    @Test
    void refresh_validRefreshToken_returnsNewTokenPair() {
        setupTestData();

        String refreshToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL, "password", PASSWORD))
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .extract().path("data.refreshToken");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
            .post("/api/auth/refresh")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.accessToken", not(emptyOrNullString()))
            .body("data.refreshToken", not(emptyOrNullString()));
    }

    @Test
    void refresh_invalidToken_returns500() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", "not-a-real-token"))
            .post("/api/auth/refresh")
        .then()
            .statusCode(500);
    }

    @Test
    void refresh_usedRefreshToken_returns500() {
        setupTestData();

        String refreshToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL, "password", PASSWORD))
            .post("/api/auth/login")
        .then().extract().path("data.refreshToken");

        // Use the refresh token once
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
            .post("/api/auth/refresh")
        .then().statusCode(200);

        // Second use must fail – token was revoked
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
            .post("/api/auth/refresh")
        .then()
            .statusCode(500);
    }

    @Test
    void jwks_alwaysReturns200() {
        given()
            .get("/api/auth/jwks")
        .then()
            .statusCode(200)
            .body("keys", notNullValue());
    }

    @Test
    void logout_withValidToken_returns200() {
        setupTestData();

        String accessToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", BO_EMAIL, "password", PASSWORD))
            .post("/api/auth/login")
        .then().extract().path("data.accessToken");

        given()
            .header("Authorization", "Bearer " + accessToken)
            .post("/api/auth/logout")
        .then()
            .statusCode(200);
    }

    @Test
    void logout_withoutToken_returns401() {
        given()
            .post("/api/auth/logout")
        .then()
            .statusCode(401);
    }
}
