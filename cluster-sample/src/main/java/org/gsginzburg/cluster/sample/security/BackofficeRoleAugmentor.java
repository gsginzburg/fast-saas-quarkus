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

package org.gsginzburg.cluster.sample.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Grants platform BACKOFFICE callers access to every tenant-scoped endpoint by elevating their
 * identity with the in-cluster roles. {@code DispatchAuthMechanism} builds the identity itself and
 * applies registered augmentors by hand, so this bean runs for cluster requests just like a normal
 * Quarkus augmentor would.
 */
@ApplicationScoped
public class BackofficeRoleAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!identity.hasRole("BACKOFFICE")) {
            return Uni.createFrom().item(identity);
        }
        return Uni.createFrom().item(
                QuarkusSecurityIdentity.builder(identity)
                        .addRole("ADMIN")
                        .addRole("TENANT")
                        .addRole("USER")
                        .addRole("READONLY")
                        .build());
    }
}
