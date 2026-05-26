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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@ApplicationScoped
@Priority(1)
public class DispatchAuthMechanism implements HttpAuthenticationMechanism {

    @Inject ClusterConfig config;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private Cache<String, ValidatedToken> tokenCache;

    @PostConstruct
    void init() {
        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .<SecurityContext>create(URI.create(config.dispatchUrl() + "/api/auth/jwks").toURL())
                    .build();

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    null, new HashSet<>(Set.of("sub", "exp"))));
            this.jwtProcessor = processor;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid dispatch URL: " + config.dispatchUrl(), e);
        }

        tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(2))
                .expireAfterAccess(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
                                              IdentityProviderManager identityProviderManager) {

        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Uni.createFrom().optional(Optional.empty());
        }

        String token = authHeader.substring(7);

        ValidatedToken cached = tokenCache.getIfPresent(token);
        if (cached != null) {
            return Uni.createFrom().item(buildIdentity(token, cached, context));
        }

        return Uni.createFrom().item(token)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(t -> {
                    ValidatedToken vt = validateLocally(t);
                    if (vt != null) {
                        tokenCache.put(t, vt);
                    }
                    return vt;
                })
                .map(vt -> {
                    if (vt == null) return null;
                    return buildIdentity(token, vt, context);
                })
                .map(Optional::ofNullable)
                .onItem().transformToUni(opt -> Uni.createFrom().optional(opt));
    }

    private ValidatedToken validateLocally(String token) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            return buildValidatedToken(claims);
        } catch (BadJOSEException | ParseException | JOSEException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ValidatedToken buildValidatedToken(JWTClaimsSet claims) {
        List<String> roles = List.of();
        Object rawRoles = claims.getClaim("roles");
        if (rawRoles instanceof List<?> list) {
            roles = list.stream().map(Object::toString).toList();
        }
        return new ValidatedToken(
                claims.getSubject(),
                (String) claims.getClaim("email"),
                (String) claims.getClaim("user_name"),
                (String) claims.getClaim("user_type"),
                (String) claims.getClaim("tenant_id"),
                (String) claims.getClaim("tenant_name"),
                (String) claims.getClaim("tenant_status"),
                (String) claims.getClaim("cluster_name"),
                (String) claims.getClaim("cluster_url"),
                roles
        );
    }

    private SecurityIdentity buildIdentity(String token, ValidatedToken vt, RoutingContext context) {
        String effectiveTenantId = resolveTenantId(vt, context);

        List<String> roles = vt.roles() != null ? vt.roles() : List.of();
        Set<String> roleSet = new HashSet<>(roles);

        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(vt.userId() != null ? vt.userId() : "unknown"))
                .addRoles(roleSet)
                .addAttribute("validatedToken", vt)
                .addAttribute("token", token)
                .addAttribute("tenantId", effectiveTenantId != null ? effectiveTenantId : "")
                .build();
    }

    private String resolveTenantId(ValidatedToken vt, RoutingContext context) {
        String path = context.normalizedPath();
        if (path.startsWith("/api/management/")) {
            return null;
        }
        String pathTenantId = context.get(PathTenantRouteFilter.ATTRIBUTE);
        if (pathTenantId != null) {
            return pathTenantId;
        }
        return vt.tenantId();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(
                new ChallengeData(401, "WWW-Authenticate", "Bearer realm=\"cluster\""));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }
}
