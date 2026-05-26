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

package org.gsginzburg.dispatch.auth.jwt;

import org.gsginzburg.dispatch.config.DispatchConfig;
import org.gsginzburg.shared.security.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchJwtServiceTest {

    private DispatchJwtService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DispatchJwtService();
        Field configField = DispatchJwtService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, testConfig());
        Method init = DispatchJwtService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    @Test
    void generateAndParse_roundTrip() {
        JwtClaims input = JwtClaims.builder()
                .sub("user-123")
                .email("test@example.com")
                .userName("Test User")
                .userType("BACKOFFICE")
                .roles(List.of("BACKOFFICE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        String token = service.generateToken(input);
        assertNotNull(token);
        assertFalse(token.isBlank());

        JwtClaims parsed = service.parseToken(token);
        assertEquals("user-123", parsed.sub());
        assertEquals("test@example.com", parsed.email());
        assertEquals("Test User", parsed.userName());
        assertEquals("BACKOFFICE", parsed.userType());
        assertEquals(List.of("BACKOFFICE"), parsed.roles());
        assertEquals("dispatch", parsed.iss());
    }

    @Test
    void generateAndParse_withTenantClaims_roundTrip() {
        JwtClaims input = JwtClaims.builder()
                .sub("user-456")
                .email("tenant@example.com")
                .userName("Tenant User")
                .userType("TENANT")
                .tenantId("tenant-abc")
                .tenantName("Acme Corp")
                .tenantStatus("ACTIVE")
                .clusterId("cluster-1")
                .clusterName("Main Cluster")
                .clusterUrl("http://cluster.example.com")
                .roles(List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        String token = service.generateToken(input);
        JwtClaims parsed = service.parseToken(token);

        assertEquals("tenant-abc", parsed.tenantId());
        assertEquals("Acme Corp", parsed.tenantName());
        assertEquals("ACTIVE", parsed.tenantStatus());
        assertEquals("cluster-1", parsed.clusterId());
        assertEquals("Main Cluster", parsed.clusterName());
        assertEquals("http://cluster.example.com", parsed.clusterUrl());
    }

    @Test
    void generateAndParse_nullOptionalClaims_roundTrip() {
        JwtClaims input = JwtClaims.builder()
                .sub("user-min")
                .email("min@example.com")
                .roles(List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtClaims parsed = service.parseToken(service.generateToken(input));
        assertNull(parsed.tenantId());
        assertNull(parsed.tenantName());
        assertNull(parsed.clusterUrl());
        assertNull(parsed.userName());
    }

    @Test
    void validateToken_valid_returnsTrue() {
        JwtClaims claims = JwtClaims.builder()
                .sub("user-456")
                .email("valid@example.com")
                .roles(List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertTrue(service.validateToken(service.generateToken(claims)));
    }

    @Test
    void validateToken_tampered_returnsFalse() {
        JwtClaims claims = JwtClaims.builder()
                .sub("user-789")
                .email("tamper@example.com")
                .roles(List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        String token = service.generateToken(claims);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".dGFtcGVyZWQ." + parts[2];
        assertFalse(service.validateToken(tampered));
    }

    @Test
    void validateToken_expired_returnsFalse() {
        JwtClaims claims = JwtClaims.builder()
                .sub("user-exp")
                .email("expired@example.com")
                .roles(List.of("USER"))
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        assertFalse(service.validateToken(service.generateToken(claims)));
    }

    @Test
    void validateToken_notAJwt_returnsFalse() {
        assertFalse(service.validateToken("not.a.valid.jwt"));
    }

    @Test
    void getPublicKeyJwks_isRS256() {
        String jwks = service.getPublicKeyJwks();
        assertNotNull(jwks);
        assertTrue(jwks.contains("\"kty\":\"RSA\""), "JWKS must contain RSA key type");
        assertTrue(jwks.contains("\"alg\":\"RS256\""), "JWKS must declare RS256 algorithm");
    }

    private static DispatchConfig testConfig() {
        return new DispatchConfig() {
            @Override
            public String schema() { return "dispatch"; }

            @Override
            public Jwt jwt() {
                return new Jwt() {
                    @Override public Optional<String> privateKeyPem() { return Optional.empty(); }
                    @Override public String issuer() { return "dispatch"; }
                    @Override public long backofficeTokenExpirySeconds() { return 28800; }
                    @Override public long userTokenExpirySeconds() { return 28800; }
                };
            }

            @Override
            public Cors cors() { return () -> "http://localhost:4200"; }

            @Override
            public Auth auth() {
                return new Auth() {
                    @Override public String provider() { return "native"; }

                    @Override
                    public Cognito cognito() {
                        return new Cognito() {
                            @Override public Optional<String> userPoolId() { return Optional.empty(); }
                            @Override public String region() { return "us-east-1"; }
                            @Override public Optional<String> jwksUrlOverride() { return Optional.empty(); }
                        };
                    }

                    @Override
                    public Firebase firebase() {
                        return new Firebase() {
                            @Override public Optional<String> projectId() { return Optional.empty(); }
                            @Override public Optional<String> jwksUrlOverride() { return Optional.empty(); }
                            @Override public Optional<String> serviceAccountJson() { return Optional.empty(); }
                        };
                    }
                };
            }

            @Override
            public Management management() { return () -> "change-me-in-production"; }
        };
    }
}
