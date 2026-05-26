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

package org.gsginzburg.cluster.sample.api;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;
import org.gsginzburg.cluster.framework.security.ValidatedToken;
import org.gsginzburg.shared.dto.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@Path("/api/app/context")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN", "BACKOFFICE", "READONLY"})
public class TenantInfoResource {

    @Inject SecurityIdentity securityIdentity;
    @Inject TenantContextHolder tenantContextHolder;

    @GET
    public Response getContext() {
        ValidatedToken vt = securityIdentity.getAttribute("validatedToken");
        if (vt == null) {
            return Response.status(500)
                    .entity(ApiResponse.error("Validated token not available")).build();
        }

        TenantContext ctx = tenantContextHolder.get();
        Map<String, Object> context = new HashMap<>();
        context.put("user", Map.of(
                "id",        vt.userId() != null ? vt.userId() : "",
                "email",     vt.email() != null ? vt.email() : "",
                "userName",  vt.userName() != null ? vt.userName() : "",
                "userType",  vt.userType() != null ? vt.userType() : "",
                "roles",     vt.roles() != null ? vt.roles() : java.util.List.of()
        ));
        if (vt.tenantId() != null) {
            context.put("tenant", Map.of(
                    "id",     vt.tenantId(),
                    "name",   vt.tenantName() != null ? vt.tenantName() : "",
                    "status", vt.tenantStatus() != null ? vt.tenantStatus() : ""
            ));
        }
        if (vt.clusterName() != null) {
            context.put("cluster", Map.of(
                    "name", vt.clusterName(),
                    "url",  vt.clusterUrl() != null ? vt.clusterUrl() : ""
            ));
        }
        context.put("localContext", Map.of(
                "userId",     ctx != null && ctx.userId() != null ? ctx.userId() : "unknown",
                "schemaName", ctx != null && ctx.tenantId() != null ? ctx.tenantId() : "unknown"
        ));

        return Response.ok(ApiResponse.ok(context)).build();
    }
}
