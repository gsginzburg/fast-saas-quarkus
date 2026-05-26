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

package org.gsginzburg.cluster.framework.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "cluster")
public interface ClusterConfig {
    String id();
    String name();
    String dispatchUrl();
    Management management();
    Map<String, ShardConfig> shards();
    PathTenantInjection pathTenantInjection();

    interface Management {
        @WithDefault("change-me-in-production")
        String apiKey();
    }

    interface ShardConfig {
        String jdbcUrl();
        String username();
        String password();
        @WithDefault("20") int maxPoolSize();
        @WithDefault("5") int minIdle();
    }

    interface PathTenantInjection {
        @WithDefault("false")
        boolean enabled();

        /** Roles whose bearers are allowed to specify a tenant via the URL path (e.g., BACKOFFICE). */
        Optional<List<String>> allowedRoles();
    }

    interface SchemaUpgrade {
        /** Number of tenant schema migrations that may run in parallel per shard. */
        @WithDefault("4")
        int parallelismPerShard();
    }

    SchemaUpgrade schemaUpgrade();
}
