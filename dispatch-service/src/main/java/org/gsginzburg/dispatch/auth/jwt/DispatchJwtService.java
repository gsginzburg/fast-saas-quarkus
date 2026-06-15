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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.config.DispatchConfig;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.JwtService;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class DispatchJwtService implements JwtService {

    @Inject
    DispatchConfig config;

    private RSAKey rsaJwk;
    private String issuer;

    private static final String KID = "dispatch-service-1050936859";

    @PostConstruct
    void init() {
        issuer = config.jwt().issuer();
        try {
            String pem = config.jwt().privateKeyPem()
                    .filter(s -> !s.isBlank())
                    .orElse(null);

            if (pem != null) {
                byte[] encoded = Base64.getDecoder().decode(pem);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
                RSAPublicKey publicKey;
                if (privateKey instanceof RSAPrivateCrtKey crtKey) {
                    publicKey = (RSAPublicKey) kf.generatePublic(
                            new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
                } else {
                    throw new IllegalStateException("Private key must be an RSAPrivateCrtKey (include CRT parameters)");
                }
                rsaJwk = new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyID(KID)
                        .build();
                log.info("JWT RS256 key loaded from configuration");
            } else {
                log.warn("No dispatch.jwt.private-key-pem configured — generating ephemeral RSA key pair. " +
                         "All tokens will be invalid after restart. Set DISPATCH_JWT_PRIVATE_KEY_PEM in production.");
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                var kp = gen.generateKeyPair();
                rsaJwk = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                        .privateKey((RSAPrivateKey) kp.getPrivate())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyID(KID)
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWT RS256 key", e);
        }
    }

    @Override
    public String generateToken(JwtClaims claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .subject(claims.sub())
                    .issuer(issuer)
                    .issueTime(Date.from(claims.issuedAt() != null ? claims.issuedAt() : Instant.now()))
                    .expirationTime(Date.from(claims.expiresAt()));

            if (claims.tenantId()     != null) builder.claim("tenant_id",     claims.tenantId());
            if (claims.clusterUrl()   != null) builder.claim("cluster_url",   claims.clusterUrl());
            if (claims.clusterId()    != null) builder.claim("cluster_id",    claims.clusterId());
            if (claims.email()        != null) builder.claim("email",         claims.email());
            if (claims.roles()        != null) builder.claim("roles",         claims.roles());
            if (claims.userName()     != null) builder.claim("user_name",     claims.userName());
            if (claims.userType()     != null) builder.claim("user_type",     claims.userType());
            if (claims.tenantName()   != null) builder.claim("tenant_name",   claims.tenantName());
            if (claims.tenantStatus() != null) builder.claim("tenant_status", claims.tenantStatus());
            if (claims.clusterName()  != null) builder.claim("cluster_name",  claims.clusterName());

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaJwk.getKeyID())
                    .build();
            SignedJWT jwt = new SignedJWT(header, builder.build());
            jwt.sign(new RSASSASigner(rsaJwk));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    @Override
    public JwtClaims parseToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(rsaJwk.toPublicJWK()))) {
                throw new RuntimeException("JWT signature verification failed");
            }
            JWTClaimsSet body = jwt.getJWTClaimsSet();
            return JwtClaims.builder()
                    .sub(body.getSubject())
                    .iss(body.getIssuer())
                    .tenantId((String) body.getClaim("tenant_id"))
                    .clusterUrl((String) body.getClaim("cluster_url"))
                    .clusterId((String) body.getClaim("cluster_id"))
                    .email((String) body.getClaim("email"))
                    .roles(body.getClaim("roles") instanceof List<?> l
                            ? l.stream().map(Object::toString).toList() : null)
                    .issuedAt(body.getIssueTime() != null ? body.getIssueTime().toInstant() : null)
                    .expiresAt(body.getExpirationTime() != null ? body.getExpirationTime().toInstant() : null)
                    .userName((String) body.getClaim("user_name"))
                    .userType((String) body.getClaim("user_type"))
                    .tenantName((String) body.getClaim("tenant_name"))
                    .tenantStatus((String) body.getClaim("tenant_status"))
                    .clusterName((String) body.getClaim("cluster_name"))
                    .build();
        } catch (ParseException | JOSEException e) {
            throw new RuntimeException("Failed to parse JWT", e);
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(rsaJwk.toPublicJWK()))) {
                return false;
            }
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            return exp != null && exp.toInstant().isAfter(Instant.now());
        } catch (ParseException | JOSEException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPublicKeyJwks() {
        try {
            return new JWKSet(rsaJwk.toPublicJWK()).toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JWKS", e);
        }
    }
}
