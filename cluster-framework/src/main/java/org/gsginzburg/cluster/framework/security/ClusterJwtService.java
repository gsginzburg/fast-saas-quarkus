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

package org.gsginzburg.cluster.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.JwtService;
import org.gsginzburg.shared.security.TokenType;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class ClusterJwtService implements JwtService {

    @Inject ClusterConfig clusterConfig;

    private SecretKey signingKey;
    private String clusterId;
    private String issuer;
    private long sessionTokenExpiry;

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(
                clusterConfig.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.clusterId = clusterConfig.id();
        this.issuer = clusterConfig.jwt().issuer();
        this.sessionTokenExpiry = clusterConfig.jwt().sessionTokenExpirySeconds();
    }

    @Override
    public String generateToken(JwtClaims claims) {
        Map<String, Object> extra = new HashMap<>();
        if (claims.type() != null)     extra.put("type", claims.type().name());
        if (claims.tenantId() != null) extra.put("tenant_id", claims.tenantId());
        if (claims.clusterId() != null) extra.put("cluster_id", claims.clusterId());
        if (claims.email() != null)    extra.put("email", claims.email());
        if (claims.roles() != null)    extra.put("roles", claims.roles());

        return Jwts.builder()
                .subject(claims.sub())
                .issuer(issuer)
                .issuedAt(Date.from(claims.issuedAt() != null ? claims.issuedAt() : Instant.now()))
                .expiration(Date.from(claims.expiresAt()))
                .claims(extra)
                .signWith(signingKey)
                .compact();
    }

    public String issueSessionToken(JwtClaims exchangeClaims) {
        Instant now = Instant.now();
        JwtClaims sessionClaims = JwtClaims.builder()
                .sub(exchangeClaims.sub())
                .type(TokenType.CLUSTER_SESSION)
                .email(exchangeClaims.email())
                .tenantId(exchangeClaims.tenantId())
                .clusterId(clusterId)
                .roles(exchangeClaims.roles())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(sessionTokenExpiry))
                .build();
        return generateToken(sessionClaims);
    }

    @Override
    public JwtClaims parseToken(String token) {
        Claims body = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return JwtClaims.builder()
                .sub(body.getSubject())
                .iss(body.getIssuer())
                .type(body.get("type") != null ? TokenType.valueOf((String) body.get("type")) : null)
                .tenantId((String) body.get("tenant_id"))
                .clusterId((String) body.get("cluster_id"))
                .email((String) body.get("email"))
                .roles(body.get("roles") instanceof List<?> list
                        ? list.stream().map(Object::toString).toList()
                        : null)
                .issuedAt(body.getIssuedAt().toInstant())
                .expiresAt(body.getExpiration().toInstant())
                .build();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPublicKeyJwks() {
        return "{\"keys\":[{\"kty\":\"oct\",\"use\":\"sig\",\"alg\":\"HS256\"}]}";
    }
}
