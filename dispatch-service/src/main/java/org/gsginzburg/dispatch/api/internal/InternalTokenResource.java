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

package org.gsginzburg.dispatch.api.internal;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.repository.AccessTokenRepository;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.ClusterInfo;
import org.gsginzburg.shared.dto.ClusterTenantInfo;
import org.gsginzburg.shared.dto.ClusterUserInfo;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Path("/api/internal")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalTokenResource {

    @Inject JwtService jwtService;
    @Inject AccessTokenRepository accessTokenRepository;
    @Inject AppUserRepository userRepository;
    @Inject TenantRepository tenantRepository;

    @POST
    @Path("/token/validate")
    @Transactional
    public Response validateToken(Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return Response.status(400)
                    .entity(ApiResponse.error("token is required")).build();
        }

        if (!jwtService.validateToken(token)) {
            return Response.status(401)
                    .entity(ApiResponse.error("Invalid or expired token")).build();
        }

        String hash = sha256Hex(token);
        Optional<org.gsginzburg.dispatch.domain.model.AccessToken> stored =
                accessTokenRepository.findByTokenHash(hash);
        if (stored.isEmpty()) {
            return Response.status(401)
                    .entity(ApiResponse.error("Token not issued by this service")).build();
        }
        if (stored.get().getExpiresAt().toInstant().isBefore(java.time.Instant.now())) {
            return Response.status(401)
                    .entity(ApiResponse.error("Token expired")).build();
        }

        JwtClaims claims = jwtService.parseToken(token);

        ClusterUserInfo userInfo = loadUserInfo(claims);
        ClusterTenantInfo tenantInfo = null;
        ClusterInfo clusterInfo = null;

        String tenantId = claims.tenantId();
        if (tenantId != null) {
            try {
                Optional<Tenant> tenantOpt = tenantRepository.findByIdWithCluster(UUID.fromString(tenantId));
                if (tenantOpt.isPresent()) {
                    Tenant tenant = tenantOpt.get();
                    tenantInfo = ClusterTenantInfo.builder()
                            .id(tenant.getId().toString())
                            .name(tenant.getName())
                            .status(tenant.getStatus().name())
                            .clusterId(tenant.getCluster().getId().toString())
                            .clusterName(tenant.getCluster().getName())
                            .clusterUrl(tenant.getCluster().getUrl())
                            .build();
                    clusterInfo = ClusterInfo.builder()
                            .id(tenant.getCluster().getId().toString())
                            .name(tenant.getCluster().getName())
                            .url(tenant.getCluster().getUrl())
                            .status(tenant.getCluster().getStatus().name())
                            .build();
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenantId in token: {}", tenantId);
            }
        }

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("claims", Map.of(
                "sub", claims.sub() != null ? claims.sub() : "",
                "tenantId", tenantId != null ? tenantId : "",
                "roles", claims.roles() != null ? claims.roles() : List.of()
        ));
        if (userInfo != null)   ctx.put("user", userInfo);
        if (tenantInfo != null) ctx.put("tenant", tenantInfo);
        if (clusterInfo != null) ctx.put("cluster", clusterInfo);

        return Response.ok(ApiResponse.ok(ctx)).build();
    }

    private ClusterUserInfo loadUserInfo(JwtClaims claims) {
        String userId = claims.sub();
        if (userId == null || userId.isBlank()) return null;
        try {
            Optional<AppUser> userOpt = userRepository.findByIdOptional(UUID.fromString(userId));
            if (userOpt.isEmpty()) return null;
            AppUser user = userOpt.get();
            return ClusterUserInfo.builder()
                    .id(user.getId().toString())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userType(user.getUserType().name())
                    .roles(claims.roles() != null ? claims.roles() : List.of())
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId in token: {}", userId);
            return null;
        }
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
