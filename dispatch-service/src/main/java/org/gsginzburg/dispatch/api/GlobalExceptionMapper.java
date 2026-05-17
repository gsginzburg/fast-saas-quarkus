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

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.exception.DispatchException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.stream.Collectors;

@Slf4j
public class GlobalExceptionMapper {

    @ServerExceptionMapper
    public Response handleDispatchException(DispatchException e) {
        return Response.status(e.getStatusCode())
                .entity(ApiResponse.error(e.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response handleConstraintViolation(ConstraintViolationException e) {
        String errors = e.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return field + ": " + cv.getMessage();
                })
                .collect(Collectors.joining("; "));
        return Response.status(400)
                .entity(ApiResponse.error(errors))
                .build();
    }

    @ServerExceptionMapper
    public Response handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return Response.status(500)
                .entity(ApiResponse.error("Internal server error"))
                .build();
    }
}
