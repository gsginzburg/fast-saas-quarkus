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

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.gsginzburg.dispatch.config.DispatchConfig;
import org.gsginzburg.shared.dto.ApiResponse;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class InternalKeyFilter implements ContainerRequestFilter {

    @Inject DispatchConfig config;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!ctx.getUriInfo().getPath().startsWith("/api/internal/")) {
            return;
        }
        String provided = ctx.getHeaderString("X-Internal-Api-Key");
        if (!config.internal().apiKey().equals(provided)) {
            ctx.abortWith(Response.status(401)
                    .entity(ApiResponse.error("Invalid or missing internal API key"))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }
}
