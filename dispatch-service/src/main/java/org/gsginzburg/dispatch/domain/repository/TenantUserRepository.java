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

package org.gsginzburg.dispatch.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.TenantUserId;
import org.gsginzburg.dispatch.domain.model.UserStatus;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TenantUserRepository implements PanacheRepositoryBase<TenantUser, TenantUserId> {

    public List<TenantUser> findByTenantId(UUID tenantId) {
        return list("tenantId", tenantId);
    }

    public List<TenantUser> findByUserId(UUID userId) {
        return list("userId", userId);
    }

    public List<TenantUser> findByTenantIdAndStatus(UUID tenantId, UserStatus status) {
        return list("tenantId = ?1 and status = ?2", tenantId, status);
    }

    public List<TenantUser> findActiveByUserId(UUID userId) {
        return list("userId = ?1 and status = ?2", userId, UserStatus.ACTIVE);
    }
}
