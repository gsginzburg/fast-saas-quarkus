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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack-based ThreadLocal holder for {@link TenantContext}.
 *
 * A stack allows nested {@code withTenant} calls: inner scopes push a new context and
 * pop it on exit, leaving the outer context intact. The per-request JWT mechanism sets
 * a single context via {@link #set} and clears it via {@link #clear}.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Deque<TenantContext>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private TenantContextHolder() {}

    public static void push(TenantContext ctx) {
        STACK.get().push(ctx);
    }

    public static TenantContext peek() {
        return STACK.get().peek();
    }

    public static TenantContext pop() {
        Deque<TenantContext> stack = STACK.get();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Tenant context stack is empty");
        }
        return stack.pop();
    }

    public static TenantContext get() {
        return peek();
    }

    public static void clear() {
        STACK.remove();
    }

    public static boolean hasContext() {
        Deque<TenantContext> stack = STACK.get();
        return stack != null && !stack.isEmpty();
    }

    public static int depth() {
        Deque<TenantContext> stack = STACK.get();
        return stack == null ? 0 : stack.size();
    }

    /**
     * Compatibility method used by the JWT auth mechanism to establish a single
     * request-level context. Clears any previous stack state then pushes the given context.
     */
    public static void set(TenantContext ctx) {
        STACK.remove();
        if (ctx != null) {
            STACK.get().push(ctx);
        }
    }
}
