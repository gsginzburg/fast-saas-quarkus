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

package org.gsginzburg.dispatch.converter;

import org.gsginzburg.dispatch.domain.dto.UserDto;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.shared.security.UserType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserConverterTest {

    private final UserConverter converter = new UserConverter();

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_mapsAllDirectFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        AppUser entity = AppUser.builder()
                .id(id)
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .userType(UserType.TENANT)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .build();

        UserDto dto = converter.toDto(entity);

        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.firstName()).isEqualTo("Alice");
        assertThat(dto.lastName()).isEqualTo("Smith");
        assertThat(dto.userType()).isEqualTo("TENANT");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.createdAt()).isEqualTo(now);
    }

    @Test
    void toDto_userTypeBackoffice_convertedToString() {
        AppUser entity = AppUser.builder()
                .id(UUID.randomUUID())
                .userType(UserType.BACKOFFICE)
                .status(UserStatus.ACTIVE)
                .build();

        assertThat(converter.toDto(entity).userType()).isEqualTo("BACKOFFICE");
    }

    @Test
    void toDto_suspendedStatus_convertedToString() {
        AppUser entity = AppUser.builder()
                .id(UUID.randomUUID())
                .status(UserStatus.SUSPENDED)
                .build();

        assertThat(converter.toDto(entity).status()).isEqualTo("SUSPENDED");
    }

    @Test
    void toDto_passwordHash_notMappedToPassword() {
        AppUser entity = AppUser.builder()
                .id(UUID.randomUUID())
                .passwordHash("$2a$12$hashed")
                .build();

        // password field in DTO has a different name than passwordHash in entity — not mapped
        assertThat(converter.toDto(entity).password()).isNull();
    }

    @Test
    void toDto_tenantMemberships_notPresentInEntity_isNull() {
        AppUser entity = AppUser.builder().id(UUID.randomUUID()).build();

        assertThat(converter.toDto(entity).tenantMemberships()).isNull();
    }

    @Test
    void toDto_nullEntity_returnsNull() {
        assertThat(converter.toDto(null)).isNull();
    }

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllDirectFields() {
        UUID id = UUID.randomUUID();

        UserDto dto = UserDto.builder()
                .id(id.toString())
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .userType("BACKOFFICE")
                .status("INACTIVE")
                .build();

        AppUser entity = converter.toDomain(dto);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getEmail()).isEqualTo("bob@example.com");
        assertThat(entity.getFirstName()).isEqualTo("Bob");
        assertThat(entity.getLastName()).isEqualTo("Jones");
        assertThat(entity.getUserType()).isEqualTo(UserType.BACKOFFICE);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void toDomain_userTypeTenant_convertedToEnum() {
        UserDto dto = UserDto.builder().userType("TENANT").build();

        assertThat(converter.toDomain(dto).getUserType()).isEqualTo(UserType.TENANT);
    }

    @Test
    void toDomain_suspendedStatus_convertedToEnum() {
        UserDto dto = UserDto.builder().status("SUSPENDED").build();

        assertThat(converter.toDomain(dto).getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void toDomain_password_notMappedToPasswordHash() {
        UserDto dto = UserDto.builder().password("plaintext").build();

        // DTO.password and entity.passwordHash have different names — not mapped
        assertThat(converter.toDomain(dto).getPasswordHash()).isNull();
    }

    @Test
    void toDomain_nullDto_returnsNull() {
        assertThat(converter.toDomain(null)).isNull();
    }
}
