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

package org.gsginzburg.cluster.framework.security;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.shared.util.Base62;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vert.x route filter that enables path-based tenant injection.
 *
 * URL shape: {@code (optional-prefix)/c/{base62TenantId}/rest-of-path}
 *
 * When a match is found the filter:
 *   1. Base62-decodes the segment to the canonical tenant UUID string.
 *   2. Stores it in the routing context under {@value ATTRIBUTE}.
 *   3. Rewrites the request URL to remove the {@code /c/{id}} segment so that
 *      normal JAX-RS routing continues against the original resource paths.
 *
 * Priority 200 ensures this runs before Quarkus authentication handlers.
 * On the second routing pass (after reroute) the path no longer matches,
 * so {@code ctx.next()} is called immediately — no infinite loop.
 */
@Slf4j
@ApplicationScoped
public class PathTenantRouteFilter {

    /** Routing-context attribute key carrying the decoded tenant UUID string. */
    public static final String ATTRIBUTE = "path-tenant-id";

    /**
     * Matches {@code (optional-prefix)/c/{base62Id}/rest}.
     * Group 1 = prefix (may be empty), group 2 = base62 id (22 alnum chars),
     * group 3 = remaining path (starts with '/').
     */
    private static final Pattern TENANT_PATH =
            Pattern.compile("^(.*?)/c/([0-9A-Za-z]{" + Base62.ENCODED_LENGTH + "})(/.+)$");

    @Inject ClusterConfig config;

    @RouteFilter(200)
    void intercept(RoutingContext ctx) {
        if (!config.pathTenantInjection().enabled()) {
            ctx.next();
            return;
        }

        String path = ctx.normalisedPath();
        Matcher m = TENANT_PATH.matcher(path);
        if (!m.matches()) {
            ctx.next();
            return;
        }

        String prefix      = m.group(1); // may be ""
        String base62Id    = m.group(2);
        String remainingPath = m.group(3);

        UUID tenantUuid;
        try {
            tenantUuid = Base62.decode(base62Id);
        } catch (IllegalArgumentException e) {
            log.debug("Path segment '{}' is not a valid base62 UUID — skipping tenant injection", base62Id);
            ctx.next();
            return;
        }

        String newPath = prefix.isEmpty() ? remainingPath : prefix + remainingPath;
        ctx.put(ATTRIBUTE, tenantUuid.toString());
        log.debug("Path tenant injection: decoded '{}' → tenantId={}, rerouting to '{}'",
                base62Id, tenantUuid, newPath);
        ctx.reroute(newPath);
    }
}
