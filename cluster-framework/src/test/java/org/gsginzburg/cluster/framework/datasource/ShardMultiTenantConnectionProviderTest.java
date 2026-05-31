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

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShardMultiTenantConnectionProviderTest {

    private static final String TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SHARD_ID   = "shard-1";
    private static final String JDBC_URL   = "jdbc:postgresql://localhost:5432/test";

    @Mock TenantShardCache        shardCache;
    @Mock ShardDataSourceRegistry registry;
    @Mock Connection               mockConn;
    @Mock Statement                mockStmt;

    @InjectMocks ShardMultiTenantConnectionProvider resolver;

    @BeforeEach
    void setUp() throws Exception {
        // lenient: not every test exercises a connection, but those that do expect this stub
        lenient().when(mockConn.createStatement()).thenReturn(mockStmt);
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_returnsConnectionProviderForKnownTenant() {
        givenTenantOnShard(TENANT_ID, SHARD_ID);

        ConnectionProvider cp = resolver.resolve(TENANT_ID);

        assertThat(cp).isNotNull();
    }

    @Test
    void resolve_throwsIllegalStateForUnknownTenant() {
        when(shardCache.getShardForTenant(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(TENANT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TENANT_ID);
    }

    // ── ConnectionProvider.getConnection ─────────────────────────────────────

    @Test
    void getConnection_routesToCorrectShardAndSetsSearchPath() throws Exception {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        when(registry.getConnection(SHARD_ID)).thenReturn(mockConn);

        Connection result = resolver.resolve(TENANT_ID).getConnection();

        assertThat(result).isSameAs(mockConn);
        verify(mockStmt).execute("SET search_path = \"" + TENANT_ID + "\"");
    }

    @Test
    void getConnection_usesSchemaNameFromShardInfo_notTenantId() throws Exception {
        String schemaName = "custom-schema-name";
        ShardInfo shard = ShardInfo.builder()
                .shardId(SHARD_ID).jdbcUrl(JDBC_URL).schemaName(schemaName).build();
        when(shardCache.getShardForTenant(TENANT_ID)).thenReturn(Optional.of(shard));
        when(registry.getConnection(SHARD_ID)).thenReturn(mockConn);

        resolver.resolve(TENANT_ID).getConnection();

        verify(mockStmt).execute("SET search_path = \"" + schemaName + "\"");
    }

    @Test
    void getConnection_closesConnectionAndRethrowsWhenSearchPathFails() throws Exception {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        when(registry.getConnection(SHARD_ID)).thenReturn(mockConn);
        doThrow(new SQLException("pg error")).when(mockStmt).execute(anyString());

        assertThatThrownBy(() -> resolver.resolve(TENANT_ID).getConnection())
                .isInstanceOf(SQLException.class)
                .hasMessage("pg error");

        // connection must be returned to pool even on failure
        verify(mockConn).close();
    }

    // ── ConnectionProvider.closeConnection ────────────────────────────────────

    @Test
    void closeConnection_resetsSearchPathToPublicThenClosesConnection() throws Exception {
        givenTenantOnShard(TENANT_ID, SHARD_ID);

        resolver.resolve(TENANT_ID).closeConnection(mockConn);

        verify(mockStmt).execute("SET search_path = public");
        verify(mockConn).close();
    }

    @Test
    void closeConnection_alwaysClosesConnectionEvenWhenResetFails() throws Exception {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        doThrow(new SQLException("reset failed")).when(mockStmt).execute(anyString());

        assertThatThrownBy(() -> resolver.resolve(TENANT_ID).closeConnection(mockConn))
                .isInstanceOf(SQLException.class);

        verify(mockConn).close();
    }

    // ── ConnectionProvider metadata ───────────────────────────────────────────

    @Test
    void handlesConnectionSchema_returnsTrue() {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        assertThat(resolver.resolve(TENANT_ID).handlesConnectionSchema()).isTrue();
    }

    @Test
    void supportsAggressiveRelease_returnsFalse() {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        assertThat(resolver.resolve(TENANT_ID).supportsAggressiveRelease()).isFalse();
    }

    @Test
    void isUnwrappableAs_returnsFalse() {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        assertThat(resolver.resolve(TENANT_ID).isUnwrappableAs(Object.class)).isFalse();
    }

    @Test
    void unwrap_throwsUnsupportedOperationException() {
        givenTenantOnShard(TENANT_ID, SHARD_ID);
        assertThatThrownBy(() -> resolver.resolve(TENANT_ID).unwrap(Object.class))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void givenTenantOnShard(String tenantId, String shardId) {
        ShardInfo shard = ShardInfo.builder()
                .shardId(shardId).jdbcUrl(JDBC_URL).schemaName(tenantId).build();
        when(shardCache.getShardForTenant(tenantId)).thenReturn(Optional.of(shard));
    }
}
