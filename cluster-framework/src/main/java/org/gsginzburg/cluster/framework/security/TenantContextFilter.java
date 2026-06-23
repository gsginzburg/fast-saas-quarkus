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

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;

import java.util.ArrayList;

/**
 * Runs on the JBoss worker thread after authentication and populates TenantContextHolder so
 * ClusterTenantResolver can find the schema. For authenticated callers the tenant comes off the
 * SecurityIdentity; for anonymous callers (guest storefront, public reads) it is resolved from the
 * {@code /c/{base62Id}} path segment that {@link PathTenantRouteFilter} decoded — otherwise guest
 * requests would have no tenant context and every tenant-scoped query would fail.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION + 1)
public class TenantContextFilter implements ContainerRequestFilter {

    @Inject SecurityIdentity securityIdentity;
    @Inject TenantContextHolder tenantContextHolder;
    @Inject CurrentVertxRequest currentVertxRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        tenantContextHolder.clear();

        if (securityIdentity.isAnonymous()) {
            String pathTenantId = pathTenantId();
            if (pathTenantId != null && !pathTenantId.isBlank()) {
                tenantContextHolder.set(TenantContext.builder()
                        .tenantId(pathTenantId)
                        .roles(new ArrayList<>())
                        .build());
            }
            return;
        }

        String tenantId = securityIdentity.getAttribute("tenantId");
        TenantContext ctx = TenantContext.builder()
                .userId(securityIdentity.getPrincipal().getName())
                .tenantId(tenantId != null && !tenantId.isBlank() ? tenantId : null)
                .roles(new ArrayList<>(securityIdentity.getRoles()))
                .build();
        tenantContextHolder.set(ctx);
    }

    /** The tenant UUID decoded from the {@code /c/{base62Id}} path, or {@code null} if not present. */
    private String pathTenantId() {
        RoutingContext rc = currentVertxRequest.getCurrent();
        return rc != null ? rc.get(PathTenantRouteFilter.ATTRIBUTE) : null;
    }
}
