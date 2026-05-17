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

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.TokenType;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@ApplicationScoped
@Priority(1)
public class ClusterJwtAuthMechanism implements HttpAuthenticationMechanism {

    @Inject ClusterJwtService jwtService;
    @Inject ClusterConfig config;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
                                              IdentityProviderManager identityProviderManager) {
        TenantContextHolder.clear();
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Uni.createFrom().optional(Optional.empty());
        }

        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            return Uni.createFrom().optional(Optional.empty());
        }

        JwtClaims claims = jwtService.parseToken(token);

        String pathTenantId = context.get(PathTenantRouteFilter.ATTRIBUTE);
        String effectiveTenantId;

        if (pathTenantId != null && config.pathTenantInjection().enabled()) {
            List<String> allowed = config.pathTenantInjection().allowedTokenTypes().orElse(List.of());
            if (!allowed.contains(claims.type().name())) {
                log.debug("Path tenant injection denied: token type {} not in allowedTokenTypes {}",
                        claims.type(), allowed);
                return Uni.createFrom().optional(Optional.empty());
            }
            effectiveTenantId = pathTenantId;
            log.debug("Path tenant injection accepted: tokenType={}, pathTenantId={}", claims.type(), pathTenantId);
        } else {
            if (claims.type() != TokenType.CLUSTER_SESSION) {
                return Uni.createFrom().optional(Optional.empty());
            }
            effectiveTenantId = claims.tenantId();
        }

        TenantContext ctx = TenantContext.builder()
                .userId(claims.sub())
                .tenantId(effectiveTenantId)
                .email(claims.email())
                .roles(claims.roles())
                .build();
        TenantContextHolder.set(ctx);

        List<String> roles = claims.roles() != null ? claims.roles() : List.of();
        Set<String> roleSet = new HashSet<>(roles);

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(claims.sub()))
                .addRoles(roleSet)
                .addAttribute("claims", claims)
                .build();

        return Uni.createFrom().item(identity);
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
