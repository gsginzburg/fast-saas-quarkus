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

package org.gsginzburg.dispatch.auth.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseAuthProviderTest {

    private static WireMockServer wireMock;
    private static RSAKey testRsaKey;

    private static final String PROJECT_ID = "test-firebase-project";
    private static final String FIREBASE_ISSUER = "https://securetoken.google.com/" + PROJECT_ID;

    @BeforeAll
    static void startWireMock() throws Exception {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        testRsaKey = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-firebase-key")
                .generate();

        String jwks = new JWKSet(testRsaKey.toPublicJWK()).toString();
        wireMock.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks)));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @Test
    void isEnabled_whenProjectIdSet_returnsTrue() throws Exception {
        assertTrue(providerWithJwks().isEnabled());
    }

    @Test
    void isEnabled_whenProjectIdNull_returnsFalse() throws Exception {
        assertFalse(disabledProvider().isEnabled());
    }

    @Test
    void getProviderId_returnsFirebase() throws Exception {
        assertEquals("firebase", providerWithJwks().getProviderId());
    }

    @Test
    void verifyTokenAndGetEmail_validToken_returnsEmail() throws Exception {
        String token = createToken("user@firebase.com", FIREBASE_ISSUER, PROJECT_ID,
                Instant.now().plusSeconds(3600));
        assertEquals("user@firebase.com", providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_expiredToken_returnsNull() throws Exception {
        String token = createToken("expired@firebase.com", FIREBASE_ISSUER, PROJECT_ID,
                Instant.now().minusSeconds(3600));
        assertNull(providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_wrongAudience_returnsNull() throws Exception {
        String token = createToken("user@firebase.com", FIREBASE_ISSUER, "wrong-project",
                Instant.now().plusSeconds(3600));
        assertNull(providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_wrongIssuer_returnsNull() throws Exception {
        String token = createToken("user@firebase.com", "https://wrong.issuer.example.com", PROJECT_ID,
                Instant.now().plusSeconds(3600));
        assertNull(providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_invalidJwt_returnsNull() throws Exception {
        assertNull(providerWithJwks().verifyTokenAndGetEmail("not.a.valid.jwt"));
    }

    private FirebaseAuthProvider providerWithJwks() throws Exception {
        FirebaseAuthProvider provider = new FirebaseAuthProvider();
        setField(provider, "projectId", PROJECT_ID);
        setField(provider, "jwtProcessor", buildProcessor());
        return provider;
    }

    private FirebaseAuthProvider disabledProvider() throws Exception {
        FirebaseAuthProvider provider = new FirebaseAuthProvider();
        setField(provider, "projectId", null);
        return provider;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor() throws Exception {
        JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                .<SecurityContext>create(URI.create(wireMock.baseUrl() + "/jwks").toURL())
                .build();
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder()
                        .issuer(FIREBASE_ISSUER)
                        .audience(List.of(PROJECT_ID))
                        .build(),
                new HashSet<>(Set.of("sub", "exp", "iat"))));
        return processor;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String createToken(String email, String issuer, String audience,
            Instant expiry) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("user-sub")
                .issuer(issuer)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiry))
                .claim("email", email);
        if (audience != null) builder.audience(audience);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(testRsaKey.getKeyID()).build(),
                builder.build());
        jwt.sign(new RSASSASigner(testRsaKey));
        return jwt.serialize();
    }
}
