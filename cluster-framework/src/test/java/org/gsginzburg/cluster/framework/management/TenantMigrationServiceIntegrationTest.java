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

package org.gsginzburg.cluster.framework.management;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.gsginzburg.cluster.framework.AbstractClusterIntegrationTest;
import org.gsginzburg.cluster.framework.datasource.ShardInfo;
import org.gsginzburg.cluster.framework.datasource.TenantShardCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class TenantMigrationServiceIntegrationTest extends AbstractClusterIntegrationTest {

    @Inject TenantMigrationService tenantMigrationService;
    @Inject TenantShardCache tenantShardCache;

    private final List<String> createdTenants = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String tenantId : createdTenants) {
            dropSchemaIfExists(tenantId);
            dropSchemaIfExists("archived-" + tenantId);
            tenantShardCache.removeTenant(tenantId);
        }
        createdTenants.clear();
    }

    @Test
    void createTenant_schemaExistsInPostgres() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);

        tenantMigrationService.createTenant(tenantId, "shard-1");

        try (Connection conn = DriverManager.getConnection(SHARD_JDBC_URL, SHARD_USER, SHARD_PASS)) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '" + tenantId + "'");
            assertThat(rs.next()).as("Schema %s must exist", tenantId).isTrue();
        }
    }

    @Test
    void createTenant_flywayMigrationsRun() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);

        tenantMigrationService.createTenant(tenantId, "shard-1");

        try (Connection conn = DriverManager.getConnection(SHARD_JDBC_URL, SHARD_USER, SHARD_PASS)) {
            ResultSet history = conn.createStatement().executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = '" + tenantId + "' AND table_name = 'flyway_schema_history'");
            assertThat(history.next())
                    .as("flyway_schema_history must exist in schema %s", tenantId).isTrue();
        }
    }

    @Test
    void createTenant_registersTenantInShardCache() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);

        tenantMigrationService.createTenant(tenantId, "shard-1");

        Optional<ShardInfo> info = tenantShardCache.getShardForTenant(tenantId);
        assertThat(info).isPresent();
        assertThat(info.get().shardId()).isEqualTo("shard-1");
        assertThat(info.get().schemaName()).isEqualTo(tenantId);
        assertThat(info.get().jdbcUrl()).contains("cluster-fw-test");
    }

    @Test
    void createTenant_newTenantIsActiveByDefault() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);

        tenantMigrationService.createTenant(tenantId, "shard-1");

        assertThat(tenantShardCache.isTenantActive(tenantId)).isTrue();
    }

    @Test
    void deleteTenant_archivesSchemaInPostgres() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);
        tenantMigrationService.createTenant(tenantId, "shard-1");

        tenantMigrationService.deleteTenant(tenantId);

        try (Connection conn = DriverManager.getConnection(SHARD_JDBC_URL, SHARD_USER, SHARD_PASS)) {
            ResultSet original = conn.createStatement().executeQuery(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '" + tenantId + "'");
            assertThat(original.next()).as("Original schema must no longer exist").isFalse();

            String archivedName = "archived-" + tenantId;
            ResultSet archived = conn.createStatement().executeQuery(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '" + archivedName + "'");
            assertThat(archived.next()).as("Archived schema %s must exist", archivedName).isTrue();
        }
    }

    @Test
    void deleteTenant_removesTenantFromShardCache() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        createdTenants.add(tenantId);
        tenantMigrationService.createTenant(tenantId, "shard-1");

        tenantMigrationService.deleteTenant(tenantId);

        assertThat(tenantShardCache.getShardForTenant(tenantId)).isEmpty();
        assertThat(tenantShardCache.isTenantActive(tenantId)).isFalse();
    }
}
