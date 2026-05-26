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
import org.gsginzburg.dispatch.converter.UserConverter;
import org.gsginzburg.dispatch.domain.dto.UserDto;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.service.UserService;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.PageDto;
import org.gsginzburg.shared.security.UserType;

import java.util.UUID;

@Path("/api/backoffice/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("BACKOFFICE")
public class UserResource {

    @Inject UserService userService;
    @Inject UserConverter userConverter;

    @GET
    public ApiResponse<PageDto<UserDto>> getUsers(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return ApiResponse.ok(userService.getUsers(page, size).map(userConverter::toDto));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<UserDto> getUser(@PathParam("id") UUID id) {
        return ApiResponse.ok(userConverter.toDto(userService.getUser(id)));
    }

    @POST
    public Response createUser(@Valid UserDto request) {
        return Response.status(201)
                .entity(ApiResponse.ok(userConverter.toDto(
                        userService.createUser(
                                request.email(),
                                request.password(),
                                request.firstName(),
                                request.lastName(),
                                UserType.valueOf(request.userType()),
                                request.authProvider()))))
                .build();
    }

    @PUT
    @Path("/{id}/status")
    public ApiResponse<UserDto> updateStatus(@PathParam("id") UUID id,
                                              @QueryParam("status") UserStatus status) {
        return ApiResponse.ok(userConverter.toDto(userService.updateUserStatus(id, status)));
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> deleteUser(@PathParam("id") UUID id) {
        userService.deleteUser(id);
        return ApiResponse.ok();
    }
}
