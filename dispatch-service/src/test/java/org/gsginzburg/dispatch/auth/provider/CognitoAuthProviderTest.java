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
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitoAuthProviderTest {

    private static WireMockServer wireMock;
    private static RSAKey testRsaKey;

    private static final String USER_POOL_ID = "us-east-1_testPool";
    private static final String REGION = "us-east-1";
    private static final String COGNITO_ISSUER =
            "https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID;

    @BeforeAll
    static void startWireMock() throws Exception {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        testRsaKey = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-cognito-key")
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
    void isEnabled_whenUserPoolIdSet_returnsTrue() throws Exception {
        assertTrue(providerWithJwks().isEnabled());
    }

    @Test
    void isEnabled_whenUserPoolIdNull_returnsFalse() throws Exception {
        assertFalse(disabledProvider().isEnabled());
    }

    @Test
    void getProviderId_returnsCognito() throws Exception {
        assertEquals("cognito", providerWithJwks().getProviderId());
    }

    @Test
    void verifyTokenAndGetEmail_validToken_returnsEmail() throws Exception {
        String token = createToken("user@example.com", COGNITO_ISSUER, null,
                Instant.now().plusSeconds(3600));
        assertEquals("user@example.com", providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_expiredToken_returnsNull() throws Exception {
        String token = createToken("expired@example.com", COGNITO_ISSUER, null,
                Instant.now().minusSeconds(3600));
        assertNull(providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_wrongIssuer_returnsNull() throws Exception {
        String token = createToken("user@example.com", "https://wrong.issuer.example.com", null,
                Instant.now().plusSeconds(3600));
        assertNull(providerWithJwks().verifyTokenAndGetEmail(token));
    }

    @Test
    void verifyTokenAndGetEmail_invalidJwt_returnsNull() throws Exception {
        assertNull(providerWithJwks().verifyTokenAndGetEmail("not.a.valid.jwt"));
    }

    private CognitoAuthProvider providerWithJwks() throws Exception {
        CognitoAuthProvider provider = new CognitoAuthProvider();
        setField(provider, "userPoolId", USER_POOL_ID);
        setField(provider, "jwtProcessor", buildProcessor(COGNITO_ISSUER, null));
        return provider;
    }

    private CognitoAuthProvider disabledProvider() throws Exception {
        CognitoAuthProvider provider = new CognitoAuthProvider();
        setField(provider, "userPoolId", null);
        return provider;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(
            String issuer, String audience) throws Exception {
        JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                .<SecurityContext>create(URI.create(wireMock.baseUrl() + "/jwks").toURL())
                .build();
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        JWTClaimsSet.Builder exact = new JWTClaimsSet.Builder().issuer(issuer);
        if (audience != null) exact.audience(audience);
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                exact.build(), new HashSet<>(Set.of("sub", "exp", "iat"))));
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
