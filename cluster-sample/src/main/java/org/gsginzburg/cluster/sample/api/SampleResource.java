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

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.gsginzburg.cluster.sample.converter.TestRecordConverter;
import org.gsginzburg.cluster.sample.domain.dto.TestRecordDto;
import org.gsginzburg.cluster.sample.service.TestRecordService;
import org.gsginzburg.shared.dto.ApiResponse;

import java.util.List;
import java.util.UUID;

@Path("/api/app/test-records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN", "BACKOFFICE", "READONLY"})
public class SampleResource {

    @Inject TestRecordService testRecordService;
    @Inject TestRecordConverter testRecordConverter;

    @GET
    public ApiResponse<List<TestRecordDto>> getAll() {
        return ApiResponse.ok(testRecordConverter.toDtoList(testRecordService.getAll()));
    }

    @POST
    public Response create(@Valid TestRecordDto request) {
        return Response.status(201)
                .entity(ApiResponse.ok(testRecordConverter.toDto(
                        testRecordService.create(request.name(), request.description(), request.value()))))
                .build();
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> delete(@PathParam("id") UUID id) {
        testRecordService.delete(id);
        return ApiResponse.ok();
    }
}
