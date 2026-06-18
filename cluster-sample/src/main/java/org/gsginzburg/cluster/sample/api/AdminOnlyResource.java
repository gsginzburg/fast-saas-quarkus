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

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.gsginzburg.shared.dto.ApiResponse;

import java.util.List;
import java.util.TreeSet;

/**
 * ADMIN-only endpoint used to exercise {@link org.gsginzburg.cluster.sample.security.BackofficeRoleAugmentor}.
 * A BACKOFFICE token carries only the {@code BACKOFFICE} role, so it can reach this endpoint solely
 * because the augmentor elevated the identity with {@code ADMIN}. The response echoes the effective
 * {@link SecurityIdentity} roles so callers can confirm augmentation occurred.
 */
@Path("/api/app/admin-only")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminOnlyResource {

    @Inject SecurityIdentity securityIdentity;

    @GET
    public ApiResponse<List<String>> adminOnly() {
        return ApiResponse.ok(List.copyOf(new TreeSet<>(securityIdentity.getRoles())));
    }
}
