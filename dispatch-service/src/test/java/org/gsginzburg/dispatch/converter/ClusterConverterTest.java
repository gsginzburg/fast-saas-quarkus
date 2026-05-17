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

import org.gsginzburg.dispatch.domain.dto.ClusterDto;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.ClusterStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterConverterTest {

    private final ClusterConverter converter = new ClusterConverter();

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    void toDto_mapsAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Cluster entity = Cluster.builder()
                .id(id)
                .name("prod-cluster")
                .url("https://prod.example.com")
                .status(ClusterStatus.ACTIVE)
                .createdAt(now)
                .build();

        ClusterDto dto = converter.toDto(entity);

        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.name()).isEqualTo("prod-cluster");
        assertThat(dto.url()).isEqualTo("https://prod.example.com");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.createdAt()).isEqualTo(now);
    }

    @Test
    void toDto_inactiveStatus_convertedToString() {
        Cluster entity = Cluster.builder()
                .id(UUID.randomUUID())
                .status(ClusterStatus.INACTIVE)
                .build();

        assertThat(converter.toDto(entity).status()).isEqualTo("INACTIVE");
    }

    @Test
    void toDto_nullEntity_returnsNull() {
        assertThat(converter.toDto(null)).isNull();
    }

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        ClusterDto dto = ClusterDto.builder()
                .id(id.toString())
                .name("staging-cluster")
                .url("https://staging.example.com")
                .status("INACTIVE")
                .createdAt(now)
                .build();

        Cluster entity = converter.toDomain(dto);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("staging-cluster");
        assertThat(entity.getUrl()).isEqualTo("https://staging.example.com");
        assertThat(entity.getStatus()).isEqualTo(ClusterStatus.INACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toDomain_activeStatus_convertedToEnum() {
        ClusterDto dto = ClusterDto.builder().status("ACTIVE").build();

        assertThat(converter.toDomain(dto).getStatus()).isEqualTo(ClusterStatus.ACTIVE);
    }

    @Test
    void toDomain_nullDto_returnsNull() {
        assertThat(converter.toDomain(null)).isNull();
    }
}
