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

package org.gsginzburg.dispatch.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.converter.TenantConverter;
import org.gsginzburg.dispatch.converter.TenantMembershipConverter;
import org.gsginzburg.dispatch.domain.dto.TenantDto;
import org.gsginzburg.dispatch.domain.dto.TenantMembershipDto;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.gsginzburg.dispatch.service.ClusterManagementClient;
import org.gsginzburg.dispatch.service.TenantService;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.PageDto;
import org.gsginzburg.shared.security.UserRole;

import java.util.List;
import java.util.UUID;

@Slf4j
@Path("/api/backoffice/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("BACKOFFICE")
public class TenantResource {

    @Inject TenantService tenantService;
    @Inject TenantConverter tenantConverter;
    @Inject TenantMembershipConverter membershipConverter;
    @Inject ClusterManagementClient clusterManagementClient;

    @GET
    public ApiResponse<PageDto<TenantDto>> getTenants(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(tenantService.getTenants(page, size).map(tenantConverter::toDto));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<TenantDto> getTenant(@PathParam("id") UUID id) {
        return ApiResponse.ok(tenantConverter.toDto(tenantService.getTenant(id)));
    }

    @POST
    public Response createTenant(@Valid TenantDto request) {
        Tenant tenant = tenantService.createTenant(request.name(), UUID.fromString(request.clusterId()));
        String apiUrl = tenant.getCluster().getApiUrl();
        if (apiUrl != null && !apiUrl.isBlank()) {
            try {
                clusterManagementClient.provisionTenant(apiUrl, tenant.getId().toString());
            } catch (Exception e) {
                log.warn("Cluster provisioning failed for tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }
        return Response.status(201)
                .entity(ApiResponse.ok(tenantConverter.toDto(tenant)))
                .build();
    }

    @POST
    @Path("/{id}/provision")
    public ApiResponse<Void> provisionTenant(@PathParam("id") UUID id) {
        Tenant tenant = tenantService.getTenant(id);
        String apiUrl = tenant.getCluster().getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new RuntimeException("Cluster has no API URL configured");
        }
        clusterManagementClient.provisionTenant(apiUrl, id.toString());
        return ApiResponse.ok();
    }

    @POST
    @Path("/{id}/upgrade")
    public ApiResponse<Void> upgradeTenant(@PathParam("id") UUID id) {
        Tenant tenant = tenantService.getTenant(id);
        String apiUrl = tenant.getCluster().getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new RuntimeException("Cluster has no API URL configured");
        }
        clusterManagementClient.upgradeTenant(apiUrl, id.toString());
        return ApiResponse.ok();
    }

    @PUT
    @Path("/{id}/status")
    public ApiResponse<TenantDto> updateStatus(@PathParam("id") UUID id,
                                                @QueryParam("status") TenantStatus status) {
        return ApiResponse.ok(tenantConverter.toDto(tenantService.updateTenantStatus(id, status)));
    }

    @PUT
    @Path("/{id}/cluster")
    public ApiResponse<TenantDto> assignCluster(@PathParam("id") UUID id,
                                                 @QueryParam("clusterId") UUID clusterId) {
        return ApiResponse.ok(tenantConverter.toDto(tenantService.assignCluster(id, clusterId)));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> deleteTenant(@PathParam("id") UUID id) {
        tenantService.deleteTenant(id);
        return ApiResponse.ok();
    }

    @GET
    @Path("/{id}/users")
    public ApiResponse<List<TenantMembershipDto>> getTenantUsers(@PathParam("id") UUID id) {
        return ApiResponse.ok(membershipConverter.toDtoList(tenantService.getTenantUsers(id)));
    }

    @POST
    @Path("/{id}/users")
    public Response assignUser(@PathParam("id") UUID id, @Valid TenantMembershipDto request) {
        tenantService.assignUser(id,
                UUID.fromString(request.userId()),
                UserRole.valueOf(request.role()));
        return Response.status(201).entity(ApiResponse.ok()).build();
    }

    @DELETE
    @Path("/{id}/users/{userId}")
    public ApiResponse<Void> removeUser(@PathParam("id") UUID id,
                                         @PathParam("userId") UUID userId) {
        tenantService.removeUser(id, userId);
        return ApiResponse.ok();
    }

}
