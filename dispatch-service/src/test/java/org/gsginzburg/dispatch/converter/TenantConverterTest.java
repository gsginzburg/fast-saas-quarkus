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

import org.gsginzburg.dispatch.domain.dto.TenantDto;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantConverterTest {

    private final TenantConverter converter = new TenantConverter();

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_mapsAllDirectFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime updatedAt = createdAt.plusHours(1);

        Tenant entity = Tenant.builder()
                .id(id)
                .name("acme-corp")
                .status(TenantStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        TenantDto dto = converter.toDto(entity);

        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.name()).isEqualTo("acme-corp");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
        assertThat(dto.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toDto_archivedStatus_convertedToString() {
        Tenant entity = Tenant.builder()
                .id(UUID.randomUUID())
                .status(TenantStatus.ARCHIVED)
                .build();

        assertThat(converter.toDto(entity).status()).isEqualTo("ARCHIVED");
    }

    @Test
    void toDto_clusterFields_notMappedByBaseConverter() {
        // clusterId, clusterName, clusterUrl require relationship navigation;
        // they are populated by overriding toDto(), not by the base converter
        Tenant entity = Tenant.builder().id(UUID.randomUUID()).build();

        TenantDto dto = converter.toDto(entity);

        assertThat(dto.clusterId()).isNull();
        assertThat(dto.clusterName()).isNull();
        assertThat(dto.clusterUrl()).isNull();
    }

    @Test
    void toDto_nullEntity_returnsNull() {
        assertThat(converter.toDto(null)).isNull();
    }

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllDirectFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime updatedAt = createdAt.plusHours(2);

        TenantDto dto = TenantDto.builder()
                .id(id.toString())
                .name("beta-tenant")
                .status("INACTIVE")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        Tenant entity = converter.toDomain(dto);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("beta-tenant");
        assertThat(entity.getStatus()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toDomain_clusterId_notMappedToClusterObject() {
        // DTO.clusterId cannot be resolved to Tenant.cluster without a lookup;
        // the base converter leaves the relationship null
        TenantDto dto = TenantDto.builder()
                .clusterId(UUID.randomUUID().toString())
                .build();

        assertThat(converter.toDomain(dto).getCluster()).isNull();
    }

    @Test
    void toDomain_allStatusValues_convertedToEnum() {
        assertThat(converter.toDomain(TenantDto.builder().status("ACTIVE").build()).getStatus())
                .isEqualTo(TenantStatus.ACTIVE);
        assertThat(converter.toDomain(TenantDto.builder().status("INACTIVE").build()).getStatus())
                .isEqualTo(TenantStatus.INACTIVE);
        assertThat(converter.toDomain(TenantDto.builder().status("ARCHIVED").build()).getStatus())
                .isEqualTo(TenantStatus.ARCHIVED);
    }

    @Test
    void toDomain_nullDto_returnsNull() {
        assertThat(converter.toDomain(null)).isNull();
    }
}
