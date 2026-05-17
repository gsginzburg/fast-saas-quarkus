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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.cluster.framework.datasource.ShardInfo;
import org.gsginzburg.cluster.framework.datasource.TenantShardCache;
import org.gsginzburg.cluster.framework.flyway.TenantSchemaManager;
import org.gsginzburg.shared.exception.TenantNotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Slf4j
@ApplicationScoped
public class TenantMigrationService {

    @Inject TenantShardCache tenantShardCache;
    @Inject PgDumpService pgDumpService;
    @Inject ClusterConfig clusterConfig;
    @Inject TenantSchemaManager schemaManager;

    public void createTenant(String tenantId, String shardId) throws Exception {
        schemaManager.createTenantSchema(tenantId, shardId);
        ClusterConfig.ShardConfig shardConfig = clusterConfig.shards().get(shardId);
        tenantShardCache.registerTenant(tenantId, ShardInfo.builder()
                .shardId(shardId)
                .jdbcUrl(shardConfig.jdbcUrl())
                .schemaName(tenantId)
                .build());
    }

    public void deleteTenant(String tenantId) throws Exception {
        ShardInfo shardInfo = tenantShardCache.getShardForTenant(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenantShardCache.setTenantStatus(tenantId, false);
        schemaManager.dropTenantSchema(tenantId, shardInfo.shardId());
        tenantShardCache.removeTenant(tenantId);
    }

    public void moveTenant(String tenantId, String targetShardId) {
        log.info("Starting tenant migration: {} -> shard {}", tenantId, targetShardId);
        Thread thread = new Thread(() -> {
            Path dumpFile = Path.of(System.getProperty("java.io.tmpdir"), tenantId + ".dump");
            try {
                ShardInfo sourceInfo = tenantShardCache.getShardForTenant(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));
                ClusterConfig.ShardConfig sourceConfig = clusterConfig.shards().get(sourceInfo.shardId());
                ClusterConfig.ShardConfig targetConfig = clusterConfig.shards().get(targetShardId);

                if (targetConfig == null) {
                    throw new IllegalArgumentException("Target shard not found: " + targetShardId);
                }

                tenantShardCache.setTenantStatus(tenantId, false);

                pgDumpService.dumpSchema(sourceConfig.jdbcUrl(), sourceConfig.username(),
                        sourceConfig.password(), tenantId, dumpFile);

                pgDumpService.restoreSchema(targetConfig.jdbcUrl(), targetConfig.username(),
                        targetConfig.password(), dumpFile);

                try (Connection conn = DriverManager.getConnection(
                        sourceConfig.jdbcUrl(), sourceConfig.username(), sourceConfig.password());
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER SCHEMA \"" + tenantId + "\" RENAME TO \"archived-" + tenantId + "\"");
                }

                tenantShardCache.registerTenant(tenantId, ShardInfo.builder()
                        .shardId(targetShardId)
                        .jdbcUrl(targetConfig.jdbcUrl())
                        .schemaName(tenantId)
                        .build());

                tenantShardCache.setTenantStatus(tenantId, true);
                log.info("Tenant migration completed: {} -> shard {}", tenantId, targetShardId);

            } catch (Exception e) {
                log.error("Tenant migration failed for {}: {}", tenantId, e.getMessage(), e);
                tenantShardCache.setTenantStatus(tenantId, true);
            } finally {
                try { Files.deleteIfExists(dumpFile); } catch (Exception ignored) {}
            }
        }, "tenant-move-" + tenantId);
        thread.setDaemon(true);
        thread.start();
    }
}
