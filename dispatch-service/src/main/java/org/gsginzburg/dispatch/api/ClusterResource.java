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
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.gsginzburg.dispatch.converter.ClusterConverter;
import org.gsginzburg.dispatch.domain.dto.ClusterDto;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.service.ClusterManagementClient;
import org.gsginzburg.dispatch.service.ClusterService;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.PageDto;

import java.util.List;
import java.util.UUID;

@Path("/api/backoffice/clusters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("BACKOFFICE")
public class ClusterResource {

    @Inject ClusterService clusterService;
    @Inject ClusterConverter clusterConverter;
    @Inject ClusterManagementClient clusterManagementClient;

    @GET
    @Path("/all")
    public ApiResponse<List<ClusterDto>> getAllClusters() {
        return ApiResponse.ok(clusterConverter.toDtoList(clusterService.getAllClusters()));
    }

    @GET
    public ApiResponse<PageDto<ClusterDto>> getClusters(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(clusterService.getClusters(page, size).map(clusterConverter::toDto));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<ClusterDto> getCluster(@PathParam("id") UUID id) {
        return ApiResponse.ok(clusterConverter.toDto(clusterService.getCluster(id)));
    }

    @POST
    public Response createCluster(@Valid ClusterDto request) {
        return Response.status(201)
                .entity(ApiResponse.ok(clusterConverter.toDto(
                        clusterService.createCluster(
                                request.name(), request.url(), request.apiUrl()))))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ApiResponse<ClusterDto> updateCluster(@PathParam("id") UUID id, @Valid ClusterDto request) {
        return ApiResponse.ok(clusterConverter.toDto(
                clusterService.updateCluster(
                        id, request.name(), request.url(), request.apiUrl())));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> deleteCluster(@PathParam("id") UUID id) {
        clusterService.deleteCluster(id);
        return ApiResponse.ok();
    }

    @POST
    @Path("/{id}/upgrade-all")
    public ApiResponse<Void> upgradeAll(@PathParam("id") UUID id) {
        Cluster cluster = clusterService.getCluster(id);
        clusterManagementClient.upgradeAll(cluster.getApiUrl());
        return ApiResponse.ok();
    }
}
