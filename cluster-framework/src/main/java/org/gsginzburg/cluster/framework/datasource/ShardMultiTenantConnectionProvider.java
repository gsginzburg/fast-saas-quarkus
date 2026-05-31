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

package org.gsginzburg.cluster.framework.datasource;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quarkus {@link TenantConnectionResolver} that routes every Hibernate session to the
 * HikariCP pool for the tenant's shard, then sets {@code search_path = "<tenantId>"}
 * so all SQL lands in the right schema.
 *
 * <p>{@link #resolve(String)} is called by Quarkus once per session open. It returns a
 * lightweight {@link ConnectionProvider} that pulls from the correct pool and manages
 * the {@code search_path} lifecycle for that session.
 *
 * <p>On connection close the {@code search_path} is reset to {@code public} before the
 * physical connection returns to HikariCP, preventing schema leakage across requests.
 *
 * <p>{@link ClusterTenantResolver} still supplies the tenant-id string; this class then
 * decides which physical pool to connect to for that id.
 */
@Slf4j
@ApplicationScoped
@PersistenceUnitExtension
public class ShardMultiTenantConnectionProvider implements TenantConnectionResolver {

    @Inject TenantShardCache        shardCache;
    @Inject ShardDataSourceRegistry registry;

    @Override
    public ConnectionProvider resolve(String tenantId) {
        ShardInfo shard = shardCache.getShardForTenant(tenantId)
                .orElseThrow(() -> new IllegalStateException("No shard found for tenant: " + tenantId));
        return new ShardConnectionProvider(registry, shard);
    }

    // ── per-session ConnectionProvider ────────────────────────────────────────

    static final class ShardConnectionProvider implements ConnectionProvider {

        private final ShardDataSourceRegistry registry;
        private final ShardInfo               shard;

        ShardConnectionProvider(ShardDataSourceRegistry registry, ShardInfo shard) {
            this.registry = registry;
            this.shard    = shard;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection conn = registry.getConnection(shard.shardId());
            try {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET search_path = \"" + shard.schemaName() + "\"");
                }
            } catch (SQLException e) {
                conn.close(); // return to pool before re-throwing
                throw e;
            }
            return conn;
        }

        @Override
        public void closeConnection(Connection conn) throws SQLException {
            // Reset before returning to the pool so the next borrower starts clean.
            try (Statement st = conn.createStatement()) {
                st.execute("SET search_path = public");
            } finally {
                conn.close();
            }
        }

        // Tells Hibernate that this provider owns schema management, so Hibernate
        // does not attempt its own search_path override on top of ours.
        @Override
        public boolean handlesConnectionSchema() {
            return true;
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            throw new UnsupportedOperationException("Unwrap not supported");
        }
    }
}
