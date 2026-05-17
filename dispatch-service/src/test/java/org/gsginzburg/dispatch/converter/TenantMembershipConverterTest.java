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

import org.gsginzburg.dispatch.domain.dto.TenantMembershipDto;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.shared.security.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMembershipConverterTest {

    private final TenantMembershipConverter converter = new TenantMembershipConverter();

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_mapsAllDirectFields() {
        UUID tenantId = UUID.randomUUID();
        UUID userId   = UUID.randomUUID();

        TenantUser entity = TenantUser.builder()
                .tenantId(tenantId)
                .userId(userId)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        TenantMembershipDto dto = converter.toDto(entity);

        assertThat(dto.tenantId()).isEqualTo(tenantId.toString());
        assertThat(dto.userId()).isEqualTo(userId.toString());
        assertThat(dto.role()).isEqualTo("ADMIN");
        assertThat(dto.status()).isEqualTo("ACTIVE");
    }

    @Test
    void toDto_allRoles_convertedToString() {
        assertThat(toDto(UserRole.BACKOFFICE).role()).isEqualTo("BACKOFFICE");
        assertThat(toDto(UserRole.ADMIN).role()).isEqualTo("ADMIN");
        assertThat(toDto(UserRole.USER).role()).isEqualTo("USER");
        assertThat(toDto(UserRole.READONLY).role()).isEqualTo("READONLY");
    }

    @Test
    void toDto_allStatuses_convertedToString() {
        assertThat(toDto(UserStatus.ACTIVE).status()).isEqualTo("ACTIVE");
        assertThat(toDto(UserStatus.INACTIVE).status()).isEqualTo("INACTIVE");
        assertThat(toDto(UserStatus.SUSPENDED).status()).isEqualTo("SUSPENDED");
    }

    @Test
    void toDto_tenantName_notPresentInEntity_isNull() {
        TenantUser entity = TenantUser.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .build();

        assertThat(converter.toDto(entity).tenantName()).isNull();
    }

    @Test
    void toDto_nullEntity_returnsNull() {
        assertThat(converter.toDto(null)).isNull();
    }

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllDirectFields() {
        UUID tenantId = UUID.randomUUID();
        UUID userId   = UUID.randomUUID();

        TenantMembershipDto dto = TenantMembershipDto.builder()
                .tenantId(tenantId.toString())
                .userId(userId.toString())
                .role("READONLY")
                .status("INACTIVE")
                .build();

        TenantUser entity = converter.toDomain(dto);

        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getRole()).isEqualTo(UserRole.READONLY);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void toDomain_allRoles_convertedToEnum() {
        assertThat(toDomain("BACKOFFICE").getRole()).isEqualTo(UserRole.BACKOFFICE);
        assertThat(toDomain("ADMIN").getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(toDomain("USER").getRole()).isEqualTo(UserRole.USER);
        assertThat(toDomain("READONLY").getRole()).isEqualTo(UserRole.READONLY);
    }

    @Test
    void toDomain_allStatuses_convertedToEnum() {
        assertThat(converter.toDomain(TenantMembershipDto.builder().status("ACTIVE").build()).getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(converter.toDomain(TenantMembershipDto.builder().status("SUSPENDED").build()).getStatus())
                .isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void toDomain_nullDto_returnsNull() {
        assertThat(converter.toDomain(null)).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TenantMembershipDto toDto(UserRole role) {
        return converter.toDto(TenantUser.builder()
                .tenantId(UUID.randomUUID()).userId(UUID.randomUUID()).role(role).build());
    }

    private TenantMembershipDto toDto(UserStatus status) {
        return converter.toDto(TenantUser.builder()
                .tenantId(UUID.randomUUID()).userId(UUID.randomUUID()).status(status).build());
    }

    private TenantUser toDomain(String role) {
        return converter.toDomain(TenantMembershipDto.builder().role(role).build());
    }
}
