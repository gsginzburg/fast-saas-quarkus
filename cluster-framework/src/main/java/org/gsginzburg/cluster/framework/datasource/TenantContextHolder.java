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

import jakarta.enterprise.context.ApplicationScoped;

/**
 * CDI-injectable ThreadLocal holder for the current request's {@link TenantContext}.
 * Holds exactly one context per thread. Inject this bean wherever tenant context is needed.
 */
@ApplicationScoped
public class TenantContextHolder {

    private final ThreadLocal<TenantContext> CTX = new ThreadLocal<>();

    public TenantContext get() {
        return CTX.get();
    }

    public void set(TenantContext ctx) {
        if (ctx == null) {
            CTX.remove();
        } else {
            CTX.set(ctx);
        }
    }

    public void clear() {
        CTX.remove();
    }

    public boolean hasContext() {
        return CTX.get() != null;
    }
}
