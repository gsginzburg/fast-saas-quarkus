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

package org.gsginzburg.cluster.framework.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DispatchAuthMechanism#applyAugmentors}, the hook that lets the mechanism
 * run registered {@link SecurityIdentityAugmentor}s even though it builds identities directly
 * (bypassing {@code IdentityProviderManager}). Without this, augmentors such as the BACKOFFICE
 * role elevator never fire and {@code @RolesAllowed} checks reject platform admins.
 */
class DispatchAuthMechanismAugmentorTest {

    /** Mirrors the production BackofficeRoleAugmentor: elevate BACKOFFICE callers with ADMIN. */
    private static final SecurityIdentityAugmentor BACKOFFICE_TO_ADMIN = new SecurityIdentityAugmentor() {
        @Override
        public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
            if (!identity.hasRole("BACKOFFICE")) {
                return Uni.createFrom().item(identity);
            }
            return Uni.createFrom().item(
                    QuarkusSecurityIdentity.builder(identity).addRole("ADMIN").build());
        }
    };

    private static SecurityIdentity identityWithRoles(String... roles) {
        QuarkusSecurityIdentity.Builder b = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("test-user"));
        for (String role : roles) {
            b.addRole(role);
        }
        return b.build();
    }

    private static SecurityIdentity apply(SecurityIdentity identity, List<SecurityIdentityAugmentor> augmentors) {
        return DispatchAuthMechanism.applyAugmentors(identity, augmentors).await().indefinitely();
    }

    @Test
    void backofficeIdentity_isElevatedWithAdminRole() {
        SecurityIdentity result = apply(identityWithRoles("BACKOFFICE"), List.of(BACKOFFICE_TO_ADMIN));

        assertThat(result.hasRole("ADMIN")).as("BACKOFFICE caller should gain ADMIN").isTrue();
        assertThat(result.hasRole("BACKOFFICE")).as("original role preserved").isTrue();
    }

    @Test
    void nonBackofficeIdentity_isLeftUnchanged() {
        SecurityIdentity result = apply(identityWithRoles("USER"), List.of(BACKOFFICE_TO_ADMIN));

        assertThat(result.hasRole("ADMIN")).as("non-BACKOFFICE caller must not be elevated").isFalse();
        assertThat(result.getRoles()).containsExactly("USER");
    }

    @Test
    void emptyAugmentorList_returnsIdentityUnchanged() {
        SecurityIdentity original = identityWithRoles("USER");
        SecurityIdentity result = apply(original, List.of());

        assertThat(result).isSameAs(original);
    }

    @Test
    void augmentorsAreChained_eachSeesThePreviousResult() {
        // Second augmentor only fires if the first already added ADMIN — proves sequential application.
        SecurityIdentityAugmentor adminToSuper = new SecurityIdentityAugmentor() {
            @Override
            public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
                if (!identity.hasRole("ADMIN")) {
                    return Uni.createFrom().item(identity);
                }
                return Uni.createFrom().item(
                        QuarkusSecurityIdentity.builder(identity).addRole("SUPER").build());
            }
        };

        SecurityIdentity result = apply(
                identityWithRoles("BACKOFFICE"),
                List.of(BACKOFFICE_TO_ADMIN, adminToSuper));

        assertThat(result.getRoles()).contains("BACKOFFICE", "ADMIN", "SUPER");
    }
}
