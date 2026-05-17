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

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.datasource.TenantShardCache;
import org.gsginzburg.cluster.framework.security.ClusterJwtService;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.exception.TenantInactiveException;
import org.gsginzburg.shared.exception.TenantNotFoundException;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.TokenType;

import java.util.Map;

@Slf4j
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthExchangeResource {

    @Inject ClusterJwtService jwtService;
    @Inject TenantShardCache tenantShardCache;

    @POST
    @Path("/exchange")
    public Response exchangeToken(Map<String, String> body) {
        String exchangeToken = body.get("exchangeToken");
        if (exchangeToken == null || exchangeToken.isBlank()) {
            return Response.status(400)
                    .entity(ApiResponse.error("exchangeToken is required")).build();
        }

        if (!jwtService.validateToken(exchangeToken)) {
            return Response.status(401)
                    .entity(ApiResponse.error("Invalid or expired token")).build();
        }

        JwtClaims claims = jwtService.parseToken(exchangeToken);
        if (claims.type() != TokenType.TENANT_EXCHANGE) {
            return Response.status(401)
                    .entity(ApiResponse.error("Not an exchange token")).build();
        }

        String tenantId = claims.tenantId();
        if (tenantShardCache.getShardForTenant(tenantId).isEmpty()) {
            throw new TenantNotFoundException(tenantId);
        }

        if (!tenantShardCache.isTenantActive(tenantId)) {
            throw new TenantInactiveException(tenantId);
        }

        String sessionToken = jwtService.issueSessionToken(claims);
        return Response.ok(ApiResponse.ok(Map.of(
                "accessToken", sessionToken,
                "tokenType", "Bearer",
                "tenantId", tenantId
        ))).build();
    }
}
