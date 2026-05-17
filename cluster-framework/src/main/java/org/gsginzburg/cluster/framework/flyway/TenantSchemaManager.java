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

package org.gsginzburg.cluster.framework.flyway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.gsginzburg.cluster.framework.config.ClusterConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Runs Flyway instance migrations inside a tenant-specific PostgreSQL schema.
 * Migrations are loaded from {@code classpath:db/migration/instance}.
 */
@Slf4j
@ApplicationScoped
public class TenantSchemaManager {

    private static final String INSTANCE_MIGRATIONS = "classpath:db/migration/instance";

    @Inject ClusterConfig clusterConfig;

    public void createTenantSchema(String tenantId, String shardId) throws Exception {
        ClusterConfig.ShardConfig shardConfig = clusterConfig.shards().get(shardId);
        if (shardConfig == null) throw new IllegalArgumentException("Shard not found: " + shardId);

        try (Connection conn = DriverManager.getConnection(
                shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + tenantId + "\"");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password())
                .schemas(tenantId)
                .defaultSchema(tenantId)
                .locations(INSTANCE_MIGRATIONS)
                .load();
        flyway.migrate();

        log.info("Created tenant schema {} on shard {}", tenantId, shardId);
    }

    public void upgradeTenantSchema(String tenantId, String shardId) {
        ClusterConfig.ShardConfig shardConfig = clusterConfig.shards().get(shardId);
        if (shardConfig == null) throw new IllegalArgumentException("Shard not found: " + shardId);

        Flyway flyway = Flyway.configure()
                .dataSource(shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password())
                .schemas(tenantId)
                .defaultSchema(tenantId)
                .locations(INSTANCE_MIGRATIONS)
                .load();
        flyway.migrate();

        log.info("Upgraded tenant schema {} on shard {}", tenantId, shardId);
    }

    public void dropTenantSchema(String tenantId, String shardId) throws Exception {
        ClusterConfig.ShardConfig shardConfig = clusterConfig.shards().get(shardId);
        if (shardConfig == null) throw new IllegalArgumentException("Shard not found: " + shardId);

        try (Connection conn = DriverManager.getConnection(
                shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password());
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER SCHEMA \"" + tenantId + "\" RENAME TO \"archived-" + tenantId + "\"");
        }
        log.info("Archived tenant schema {} on shard {}", tenantId, shardId);
    }
}
