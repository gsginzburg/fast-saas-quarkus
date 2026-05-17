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

package org.gsginzburg.cluster.framework.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.gsginzburg.cluster.framework.datasource.TenantShardCache;
import org.gsginzburg.cluster.framework.management.TenantMigrationService;
import org.gsginzburg.cluster.framework.management.TenantUpgradeResult;
import org.gsginzburg.cluster.framework.management.TenantUpgradeService;
import org.gsginzburg.shared.dto.ApiResponse;

import java.util.List;
import java.util.Map;

@Path("/api/management/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("BACKOFFICE")
public class TenantManagementResource {

    @Inject TenantMigrationService migrationService;
    @Inject TenantUpgradeService upgradeService;
    @Inject TenantShardCache tenantShardCache;

    @POST
    public Response createTenant(Map<String, String> body) throws Exception {
        String tenantId = body.get("tenantId");
        String shardId = body.get("shardId");
        migrationService.createTenant(tenantId, shardId);
        return Response.status(201).entity(ApiResponse.ok()).build();
    }

    @DELETE
    @Path("/{tenantId}")
    public Response deleteTenant(@PathParam("tenantId") String tenantId) throws Exception {
        migrationService.deleteTenant(tenantId);
        return Response.ok(ApiResponse.ok()).build();
    }

    @PUT
    @Path("/{tenantId}/status")
    public Response setTenantStatus(@PathParam("tenantId") String tenantId,
                                    @QueryParam("active") boolean active) {
        tenantShardCache.setTenantStatus(tenantId, active);
        return Response.ok(ApiResponse.ok()).build();
    }

    @POST
    @Path("/{tenantId}/move")
    public Response moveTenant(@PathParam("tenantId") String tenantId,
                               @QueryParam("targetShardId") String targetShardId) {
        migrationService.moveTenant(tenantId, targetShardId);
        return Response.accepted(ApiResponse.accepted("Migration started asynchronously")).build();
    }

    @GET
    public Response listTenants() {
        return Response.ok(ApiResponse.ok(tenantShardCache.getAllTenants())).build();
    }

    @POST
    @Path("/{tenantId}/upgrade")
    public Response upgradeTenant(@PathParam("tenantId") String tenantId) {
        TenantUpgradeResult result = upgradeService.upgradeOneTenant(tenantId);
        if (result.success()) {
            return Response.ok(ApiResponse.ok(result)).build();
        }
        return Response.status(500).entity(ApiResponse.error(result.error())).build();
    }

    @POST
    @Path("/upgrade-all")
    public Response upgradeAllTenants() {
        List<TenantUpgradeResult> results = upgradeService.upgradeAllTenants();
        boolean allOk = results.stream().allMatch(TenantUpgradeResult::success);
        int status = allOk ? 200 : 207;
        return Response.status(status).entity(ApiResponse.ok(results)).build();
    }
}
