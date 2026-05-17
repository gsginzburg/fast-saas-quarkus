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
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;
import org.gsginzburg.cluster.sample.client.DispatchClientImpl;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.ClusterInfo;
import org.gsginzburg.shared.dto.ClusterTenantInfo;
import org.gsginzburg.shared.dto.ClusterUserInfo;
import org.gsginzburg.shared.security.JwtClaims;

import java.util.Map;

@Path("/api/app/context")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
public class TenantInfoResource {

    @Inject DispatchClientImpl dispatchClient;
    @Inject SecurityIdentity securityIdentity;

    @GET
    public ApiResponse<Map<String, Object>> getContext() {
        JwtClaims claims = securityIdentity.getAttribute("claims");
        TenantContext ctx = TenantContextHolder.get();

        ClusterTenantInfo tenantInfo = dispatchClient.getTenantInfo(claims.tenantId());
        ClusterUserInfo userInfo = dispatchClient.getUserInfo(claims.sub());
        ClusterInfo clusterInfo = dispatchClient.getClusterInfo(claims.clusterId());

        Map<String, Object> context = Map.of(
                "tenant", tenantInfo,
                "user", userInfo,
                "cluster", clusterInfo,
                "localContext", Map.of(
                        "userId", ctx != null ? ctx.userId() : "unknown",
                        "schemaName", ctx != null ? ctx.tenantId() : "unknown"
                )
        );
        return ApiResponse.ok(context);
    }
}
