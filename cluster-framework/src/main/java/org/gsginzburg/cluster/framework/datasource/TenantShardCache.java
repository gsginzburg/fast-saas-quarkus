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

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.shared.util.UuidUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@ApplicationScoped
public class TenantShardCache {

    @Inject ClusterConfig clusterConfig;

    private volatile Map<String, ShardInfo> tenantShardMap = new ConcurrentHashMap<>();
    private volatile Map<String, Boolean> tenantStatusMap = new ConcurrentHashMap<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Scheduled(every = "10m", delayed = "10m")
    public void scheduledRefresh() {
        refresh();
    }

    public void refresh() {
        log.info("Refreshing tenant-shard cache from {} configured shards", clusterConfig.shards().size());
        Map<String, ShardInfo> newMap = new HashMap<>();

        for (Map.Entry<String, ClusterConfig.ShardConfig> entry : clusterConfig.shards().entrySet()) {
            String shardId = entry.getKey();
            ClusterConfig.ShardConfig shardConfig = entry.getValue();
            try {
                scanShard(shardId, shardConfig, newMap);
            } catch (Exception e) {
                log.error("Failed to scan shard {}: {}", shardId, e.getMessage(), e);
            }
        }

        lock.writeLock().lock();
        try {
            tenantShardMap = new ConcurrentHashMap<>(newMap);
            for (String tenantId : newMap.keySet()) {
                tenantStatusMap.putIfAbsent(tenantId, true);
            }
        } finally {
            lock.writeLock().unlock();
        }
        log.info("Tenant-shard cache refreshed: {} tenants across {} shards",
                newMap.size(), clusterConfig.shards().size());
    }

    private void scanShard(String shardId, ClusterConfig.ShardConfig shardConfig,
                           Map<String, ShardInfo> accumulator) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                shardConfig.jdbcUrl(), shardConfig.username(), shardConfig.password())) {

            List<String> uuidSchemas = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_schema FROM information_schema.tables " +
                    "WHERE table_name = 'flyway_schema_history'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String schemaName = rs.getString(1);
                    if (UuidUtils.isUuid(schemaName)) {
                        uuidSchemas.add(schemaName);
                    }
                }
            }

            for (String schemaName : uuidSchemas) {
                accumulator.put(schemaName, ShardInfo.builder()
                        .shardId(shardId)
                        .jdbcUrl(shardConfig.jdbcUrl())
                        .schemaName(schemaName)
                        .build());
            }
        }
    }

    public Optional<ShardInfo> getShardForTenant(String tenantId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(tenantShardMap.get(tenantId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isTenantActive(String tenantId) {
        lock.readLock().lock();
        try {
            return tenantStatusMap.getOrDefault(tenantId, false);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setTenantStatus(String tenantId, boolean active) {
        lock.writeLock().lock();
        try {
            tenantStatusMap.put(tenantId, active);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void registerTenant(String tenantId, ShardInfo shardInfo) {
        lock.writeLock().lock();
        try {
            tenantShardMap.put(tenantId, shardInfo);
            tenantStatusMap.put(tenantId, true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeTenant(String tenantId) {
        lock.writeLock().lock();
        try {
            tenantShardMap.remove(tenantId);
            tenantStatusMap.remove(tenantId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, ShardInfo> getAllTenants() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(tenantShardMap));
        } finally {
            lock.readLock().unlock();
        }
    }
}
