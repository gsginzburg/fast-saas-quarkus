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

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.ClusterInfo;
import org.gsginzburg.shared.dto.ClusterTenantInfo;
import org.gsginzburg.shared.dto.ClusterUserInfo;

import java.util.UUID;

/**
 * Read-only API consumed by cluster services to query dispatch data.
 */
@Path("/api/dispatch")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class DispatchQueryResource {

    @Inject TenantRepository tenantRepository;
    @Inject AppUserRepository userRepository;
    @Inject ClusterRepository clusterRepository;

    @GET
    @Path("/tenant/{tenantId}")
    public ApiResponse<ClusterTenantInfo> getTenantInfo(@PathParam("tenantId") UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdOptional(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
        ClusterTenantInfo info = ClusterTenantInfo.builder()
                .id(tenant.getId().toString())
                .name(tenant.getName())
                .status(tenant.getStatus().name())
                .clusterId(tenant.getCluster().getId().toString())
                .clusterName(tenant.getCluster().getName())
                .clusterUrl(tenant.getCluster().getUrl())
                .build();
        return ApiResponse.ok(info);
    }

    @GET
    @Path("/user/{userId}")
    public ApiResponse<ClusterUserInfo> getUserInfo(@PathParam("userId") UUID userId) {
        return userRepository.findByIdOptional(userId)
                .map(u -> ClusterUserInfo.builder()
                        .id(u.getId().toString())
                        .email(u.getEmail())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .userType(u.getUserType().name())
                        .build())
                .map(ApiResponse::ok)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    @GET
    @Path("/cluster/{clusterId}")
    public ApiResponse<ClusterInfo> getClusterInfo(@PathParam("clusterId") UUID clusterId) {
        return clusterRepository.findByIdOptional(clusterId)
                .map(c -> ClusterInfo.builder()
                        .id(c.getId().toString())
                        .name(c.getName())
                        .url(c.getUrl())
                        .status(c.getStatus().name())
                        .build())
                .map(ApiResponse::ok)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
    }

    @GET
    @Path("/jwks")
    public String getJwks() {
        return "{\"keys\":[{\"kty\":\"oct\",\"use\":\"sig\",\"alg\":\"HS256\"}]}";
    }
}
