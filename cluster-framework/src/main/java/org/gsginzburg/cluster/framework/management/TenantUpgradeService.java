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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ApplicationScoped
public class TenantUpgradeService {

    @Inject TenantShardCache tenantShardCache;
    @Inject TenantSchemaManager schemaManager;
    @Inject ClusterConfig clusterConfig;

    /**
     * Upgrades a single tenant's schema by running all pending Flyway migrations.
     * Runs synchronously on the calling thread.
     */
    public TenantUpgradeResult upgradeOneTenant(String tenantId) {
        ShardInfo shard = tenantShardCache.getShardForTenant(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return runUpgrade(tenantId, shard.shardId());
    }

    /**
     * Upgrades every tenant schema across all shards.
     *
     * For each shard a dedicated thread pool of {@code parallelismPerShard} threads is
     * created before any work begins. All tenant migrations are submitted to their
     * respective shard pool so migrations across shards run concurrently. The method
     * blocks until every migration has finished, then shuts down every pool.
     */
    public List<TenantUpgradeResult> upgradeAllTenants() {
        Map<String, List<String>> tenantsByShard = groupTenantsByShard();
        if (tenantsByShard.isEmpty()) {
            log.info("No tenants found in shard cache — nothing to upgrade");
            return List.of();
        }

        int parallelism = clusterConfig.schemaUpgrade().parallelismPerShard();
        int totalTenants = tenantsByShard.values().stream().mapToInt(List::size).sum();
        log.info("Starting cluster-wide schema upgrade: {} shards, {} tenants, parallelism={} per shard",
                tenantsByShard.size(), totalTenants, parallelism);

        // Create one fixed thread pool per shard at the beginning of the upgrade process
        Map<String, ExecutorService> shardPools = new LinkedHashMap<>();
        for (String shardId : tenantsByShard.keySet()) {
            AtomicInteger counter = new AtomicInteger();
            shardPools.put(shardId, Executors.newFixedThreadPool(parallelism,
                    r -> new Thread(r, "schema-upgrade-" + shardId + "-" + counter.incrementAndGet())));
            log.info("Created upgrade thread pool for shard {} (parallelism={})", shardId, parallelism);
        }

        // Submit every tenant to its shard pool; track futures alongside their tenant/shard identity
        record PendingUpgrade(String tenantId, String shardId, Future<TenantUpgradeResult> future) {}
        List<PendingUpgrade> pending = new ArrayList<>(totalTenants);
        for (Map.Entry<String, List<String>> entry : tenantsByShard.entrySet()) {
            String shardId = entry.getKey();
            ExecutorService pool = shardPools.get(shardId);
            for (String tenantId : entry.getValue()) {
                pending.add(new PendingUpgrade(tenantId, shardId,
                        pool.submit(() -> runUpgrade(tenantId, shardId))));
            }
        }

        // Collect results — futures across different shard pools overlap in time
        List<TenantUpgradeResult> results = new ArrayList<>(pending.size());
        for (PendingUpgrade p : pending) {
            try {
                results.add(p.future().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(TenantUpgradeResult.failure(p.tenantId(), p.shardId(), "Interrupted"));
            } catch (ExecutionException e) {
                // runUpgrade catches all exceptions internally; this path is a safety net
                results.add(TenantUpgradeResult.failure(p.tenantId(), p.shardId(),
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            }
        }

        // Terminate all shard pools at the end of the upgrade process
        shardPools.forEach((shardId, pool) -> {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("Thread pool for shard {} did not terminate within 60s", shardId);
                    pool.shutdownNow();
                }
                log.info("Terminated upgrade thread pool for shard {}", shardId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        });

        long succeeded = results.stream().filter(TenantUpgradeResult::success).count();
        log.info("Cluster-wide schema upgrade complete: {}/{} tenants succeeded", succeeded, results.size());
        return results;
    }

    private TenantUpgradeResult runUpgrade(String tenantId, String shardId) {
        log.info("Starting schema upgrade for tenant {} on shard {}", tenantId, shardId);
        try {
            schemaManager.upgradeTenantSchema(tenantId, shardId);
            log.info("Schema upgrade succeeded for tenant {} on shard {}", tenantId, shardId);
            return TenantUpgradeResult.success(tenantId, shardId);
        } catch (Exception e) {
            log.error("Schema upgrade failed for tenant {} on shard {}: {}", tenantId, shardId, e.getMessage(), e);
            return TenantUpgradeResult.failure(tenantId, shardId, e.getMessage());
        }
    }

    private Map<String, List<String>> groupTenantsByShard() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ShardInfo> e : tenantShardCache.getAllTenants().entrySet()) {
            result.computeIfAbsent(e.getValue().shardId(), k -> new ArrayList<>()).add(e.getKey());
        }
        return result;
    }
}
