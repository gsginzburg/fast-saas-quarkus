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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.gsginzburg.dispatch.auth.jwt.DispatchJwtService;
import org.gsginzburg.dispatch.domain.dto.ExternalLoginRequest;
import org.gsginzburg.dispatch.domain.dto.LoginRequest;
import org.gsginzburg.dispatch.domain.dto.LoginResponse;
import org.gsginzburg.dispatch.domain.dto.RefreshTokenRequest;
import org.gsginzburg.dispatch.domain.dto.ScopeToTenantRequest;
import org.gsginzburg.dispatch.domain.dto.ScopedTokenResponse;
import org.gsginzburg.dispatch.service.AuthService;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.security.JwtClaims;

import java.util.UUID;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthService authService;
    @Inject DispatchJwtService jwtService;
    @Inject SecurityIdentity identity;

    @POST
    @Path("/login")
    public ApiResponse<LoginResponse> login(@Valid LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @POST
    @Path("/login/external")
    public ApiResponse<LoginResponse> loginExternal(@Valid ExternalLoginRequest request) {
        return ApiResponse.ok(authService.loginWithExternalToken(request.externalToken(), request.provider()));
    }

    @POST
    @Path("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refreshToken(request.refreshToken()));
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response logout() {
        JwtClaims claims = identity.getAttribute("claims");
        authService.logout(UUID.fromString(claims.sub()));
        return Response.ok(ApiResponse.ok()).build();
    }

    @POST
    @Path("/scope-to-tenant")
    @RolesAllowed("BACKOFFICE")
    public ApiResponse<ScopedTokenResponse> scopeToTenant(@Valid ScopeToTenantRequest request) {
        JwtClaims claims = identity.getAttribute("claims");
        return ApiResponse.ok(authService.scopeToTenant(claims.sub(), request.tenantId()));
    }

    @GET
    @Path("/jwks")
    @Produces(MediaType.APPLICATION_JSON)
    public String getJwks() {
        return jwtService.getPublicKeyJwks();
    }
}
