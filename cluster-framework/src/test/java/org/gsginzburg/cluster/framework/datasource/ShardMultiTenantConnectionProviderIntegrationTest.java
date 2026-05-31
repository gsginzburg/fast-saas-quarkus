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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.gsginzburg.cluster.framework.AbstractClusterIntegrationTest;
import org.gsginzburg.cluster.framework.management.TenantMigrationService;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ShardMultiTenantConnectionProvider} against a real PostgreSQL
 * instance. Hibernate ORM is disabled in the cluster-framework test profile so the provider
 * bean is exercised directly via CDI, decoupled from the Hibernate session lifecycle.
 */
@QuarkusTest
class ShardMultiTenantConnectionProviderIntegrationTest extends AbstractClusterIntegrationTest {

    @Inject @PersistenceUnitExtension ShardMultiTenantConnectionProvider resolver;
    @Inject TenantShardCache                                              tenantShardCache;
    @Inject TenantMigrationService                                        tenantMigrationService;

    private final List<String> schemasToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String schema : schemasToCleanup) {
            dropSchemaIfExists(schema);
            tenantShardCache.removeTenant(schema);
        }
        schemasToCleanup.clear();
    }

    // ── resolve + getConnection ───────────────────────────────────────────────

    @Test
    void getConnection_setsSearchPathToTenantSchema() throws Exception {
        String tenantId = registerFreshTenant();
        ConnectionProvider cp = resolver.resolve(tenantId);

        Connection conn = cp.getConnection();
        try {
            assertThat(searchPath(conn)).contains(tenantId);
        } finally {
            cp.closeConnection(conn);
        }
    }

    @Test
    void resolve_throwsIllegalStateForUnknownTenant() {
        String unknownId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> resolver.resolve(unknownId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unknownId);
    }

    @Test
    void twoTenants_eachConnectionHasItsOwnSearchPath() throws Exception {
        String tenantA = registerFreshTenant();
        String tenantB = registerFreshTenant();

        ConnectionProvider cpA = resolver.resolve(tenantA);
        ConnectionProvider cpB = resolver.resolve(tenantB);
        Connection connA = cpA.getConnection();
        Connection connB = cpB.getConnection();
        try {
            assertThat(searchPath(connA)).contains(tenantA).doesNotContain(tenantB);
            assertThat(searchPath(connB)).contains(tenantB).doesNotContain(tenantA);
        } finally {
            cpA.closeConnection(connA);
            cpB.closeConnection(connB);
        }
    }

    // ── release + re-acquire lifecycle ────────────────────────────────────────

    @Test
    void closeAndReacquire_searchPathIsCorrectOnSecondAcquire() throws Exception {
        String tenantId = registerFreshTenant();
        ConnectionProvider cp = resolver.resolve(tenantId);

        Connection conn1 = cp.getConnection();
        assertThat(searchPath(conn1)).contains(tenantId);
        cp.closeConnection(conn1);

        // Pool may return same physical connection; search_path must be re-set correctly.
        Connection conn2 = cp.getConnection();
        try {
            assertThat(searchPath(conn2)).contains(tenantId);
        } finally {
            cp.closeConnection(conn2);
        }
    }

    // ── Flyway ↔ connection-provider alignment ────────────────────────────────

    /**
     * End-to-end proof that Flyway and the connection provider use the same shard.
     *
     * <p>createTenant runs Flyway against the shard's JDBC URL and creates a schema.
     * resolve() then asks TenantShardCache for the same shard and opens a HikariCP
     * connection with search_path set to that schema.  If the two paths pointed to
     * different databases the query below would fail with "relation does not exist".
     */
    @Test
    void afterCreateTenant_resolvedConnectionReachesFlywayMigratedSchema() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        schemasToCleanup.add(tenantId);

        // Flyway creates the schema (+ flyway_schema_history) on shard-1.
        tenantMigrationService.createTenant(tenantId, "shard-1");

        // The connection provider must route to that exact shard + schema.
        ConnectionProvider cp = resolver.resolve(tenantId);
        Connection conn = cp.getConnection();
        try {
            // search_path is already set to tenantId by getConnection(), so the
            // unqualified table name resolves to "<tenantId>".flyway_schema_history.
            // This query succeeds only if the provider reached the right database+schema.
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM flyway_schema_history");
            rs.next();
            assertThat(rs.getInt(1)).as("flyway_schema_history must be accessible via the provider connection")
                    .isGreaterThanOrEqualTo(0);

            // Also verify the search_path is the tenant's own schema, not the default.
            assertThat(searchPath(conn)).contains(tenantId);
        } finally {
            cp.closeConnection(conn);
        }
    }

    @Test
    void createTenant_shardCacheAndConnectionProviderAgreeOnShard() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        schemasToCleanup.add(tenantId);

        tenantMigrationService.createTenant(tenantId, "shard-1");

        // Cache must record shard-1 with the same JDBC URL that ClusterConfig has.
        ShardInfo cached = tenantShardCache.getShardForTenant(tenantId).orElseThrow();
        assertThat(cached.shardId()).isEqualTo("shard-1");
        assertThat(cached.jdbcUrl()).isEqualTo(SHARD_JDBC_URL);
        assertThat(cached.schemaName()).isEqualTo(tenantId);

        // Connection provider must open against that exact shard and schema.
        ConnectionProvider cp = resolver.resolve(tenantId);
        Connection conn = cp.getConnection();
        try {
            assertThat(searchPath(conn)).contains(tenantId);

            // current_database() must match the shard's database in the JDBC URL.
            ResultSet rs = conn.createStatement().executeQuery("SELECT current_database()");
            rs.next();
            String currentDb = rs.getString(1);
            assertThat(SHARD_JDBC_URL).as("JDBC URL must contain the database the provider connected to")
                    .contains(currentDb);
        } finally {
            cp.closeConnection(conn);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String registerFreshTenant() {
        String tenantId = UUID.randomUUID().toString();
        schemasToCleanup.add(tenantId);
        tenantShardCache.registerTenant(tenantId, ShardInfo.builder()
                .shardId("shard-1")
                .jdbcUrl(SHARD_JDBC_URL)
                .schemaName(tenantId)
                .build());
        return tenantId;
    }

    private static String searchPath(Connection conn) throws Exception {
        ResultSet rs = conn.createStatement().executeQuery("SHOW search_path");
        rs.next();
        return rs.getString(1);
    }
}
