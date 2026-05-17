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

package org.gsginzburg.cluster.framework.tenant;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;

/**
 * Quarkus schema-based multi-tenancy resolver.
 *
 * Returns the tenant UUID as the schema name so Hibernate sets
 * {@code search_path = "<tenantId>"} on every connection acquired
 * for a tenant request.
 */
@ApplicationScoped
@PersistenceUnitExtension
public class ClusterTenantResolver implements TenantResolver {

    @Override
    public String getDefaultTenantId() {
        return "public";
    }

    @Override
    public String resolveTenantId() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.tenantId() == null || ctx.tenantId().isBlank()) {
            return getDefaultTenantId();
        }
        return ctx.tenantId();
    }
}
