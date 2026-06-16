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

import io.quarkus.arc.InactiveBeanException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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

    /**
     * Quarkus-managed Flyway for the default datasource, used only as the source of its
     * build-time {@link ResourceProvider}. Reusing that provider lets the per-tenant
     * Flyway below discover migrations in a GraalVM native image, where Flyway's own
     * runtime classpath scan finds nothing. Wrapped in {@link Instance} because some
     * deployments (e.g. framework tests with no active datasource) have no Flyway bean.
     */
    @Inject Instance<Flyway> managedFlyway;

    public void createTenantSchema(String tenantId, String shardId) throws Exception {
        ClusterConfig.ShardConfig shardConfig = clusterConfig.shards().get(shardId);
        if (shardConfig == null) throw new IllegalArgumentException("Shard not found: " + shardId);

        try (Connection conn = DriverManager.getConnection(
                shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + tenantId + "\"");
        }

        FluentConfiguration config = Flyway.configure()
                .dataSource(shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password())
                .schemas(tenantId)
                .defaultSchema(tenantId)
                .locations(INSTANCE_MIGRATIONS);

        // Reuse Quarkus's build-time resource provider when an active managed Flyway exists,
        // so migrations are discovered in a native image (Flyway's runtime classpath scan
        // returns nothing there). Falls back to classpath scanning otherwise.
        ResourceProvider provider = managedResourceProvider();
        if (provider != null) {
            config.resourceProvider(provider);
        }

        config.load().migrate();

        log.info("Created tenant schema {} on shard {}", tenantId, shardId);
    }

    /**
     * The build-time resource provider from the Quarkus-managed Flyway, or {@code null}
     * when no active Flyway bean is present (e.g. a deployment/test with no active
     * datasource), in which case Flyway falls back to default classpath scanning.
     */
    private ResourceProvider managedResourceProvider() {
        if (!managedFlyway.isResolvable()) {
            return null;
        }
        try {
            return managedFlyway.get().getConfiguration().getResourceProvider();
        } catch (InactiveBeanException e) {
            return null;
        }
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
