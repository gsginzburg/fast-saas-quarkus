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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a HikariCP connection pool per configured shard.
 * Provides pooled connections to application code and schema management operations.
 */
@Slf4j
@ApplicationScoped
public class ShardDataSourceRegistry {

    @Inject ClusterConfig clusterConfig;

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        for (Map.Entry<String, ClusterConfig.ShardConfig> entry : clusterConfig.shards().entrySet()) {
            String shardId = entry.getKey();
            ClusterConfig.ShardConfig cfg = entry.getValue();
            if (cfg.jdbcUrl() == null || cfg.jdbcUrl().isBlank()) {
                log.warn("Shard {} has no jdbcUrl configured, skipping pool creation", shardId);
                continue;
            }
            try {
                HikariConfig hikari = new HikariConfig();
                hikari.setJdbcUrl(cfg.jdbcUrl());
                hikari.setUsername(cfg.username());
                hikari.setPassword(cfg.password());
                hikari.setMaximumPoolSize(cfg.maxPoolSize());
                hikari.setMinimumIdle(cfg.minIdle());
                hikari.setPoolName("shard-" + shardId);
                pools.put(shardId, new HikariDataSource(hikari));
                log.info("Created HikariCP pool for shard {}", shardId);
            } catch (Exception e) {
                log.error("Failed to create pool for shard {}: {}", shardId, e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(ds -> {
            try { ds.close(); } catch (Exception ignored) {}
        });
    }

    public DataSource getDataSource(String shardId) {
        HikariDataSource ds = pools.get(shardId);
        if (ds == null) throw new IllegalArgumentException("No pool configured for shard: " + shardId);
        return ds;
    }

    public Connection getConnection(String shardId) throws SQLException {
        return getDataSource(shardId).getConnection();
    }
}
