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

package org.gsginzburg.dispatch.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.TenantUserId;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.dto.PageDto;
import org.gsginzburg.shared.exception.TenantNotFoundException;
import org.gsginzburg.shared.security.UserRole;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TenantService {

    @Inject TenantRepository tenantRepository;
    @Inject ClusterRepository clusterRepository;
    @Inject TenantUserRepository tenantUserRepository;

    public PageDto<Tenant> getTenants(int page, int size) {
        var query = tenantRepository.queryAll();
        long total = query.count();
        List<Tenant> content = query.page(page, size).list();
        return PageDto.<Tenant>builder()
                .content(content)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .pageNumber(page)
                .pageSize(size)
                .build();
    }

    public Tenant getTenant(UUID id) {
        return findTenant(id);
    }

    @Transactional
    public Tenant createTenant(String name, UUID clusterId) {
        Cluster cluster = clusterRepository.findByIdOptional(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
        Tenant tenant = Tenant.builder()
                .name(name)
                .cluster(cluster)
                .status(TenantStatus.ACTIVE)
                .build();
        tenantRepository.persist(tenant);
        return tenant;
    }

    @Transactional
    public Tenant updateTenantStatus(UUID id, TenantStatus status) {
        Tenant tenant = findTenant(id);
        tenant.setStatus(status);
        return tenant;
    }

    @Transactional
    public Tenant assignCluster(UUID id, UUID clusterId) {
        Tenant tenant = findTenant(id);
        Cluster cluster = clusterRepository.findByIdOptional(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
        tenant.setCluster(cluster);
        return tenant;
    }

    @Transactional
    public void deleteTenant(UUID id) {
        findTenant(id).setStatus(TenantStatus.ARCHIVED);
    }

    public List<TenantUser> getTenantUsers(UUID tenantId) {
        return tenantUserRepository.findByTenantId(tenantId);
    }

    @Transactional
    public void assignUser(UUID tenantId, UUID userId, UserRole role) {
        TenantUser tu = TenantUser.builder()
                .tenantId(tenantId)
                .userId(userId)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        tenantUserRepository.persist(tu);
    }

    @Transactional
    public void removeUser(UUID tenantId, UUID userId) {
        TenantUser tu = tenantUserRepository.findByIdOptional(new TenantUserId(tenantId, userId))
                .orElseThrow(() -> new RuntimeException("User not in tenant"));
        tu.setStatus(UserStatus.INACTIVE);
    }

    private Tenant findTenant(UUID id) {
        return tenantRepository.findByIdOptional(id)
                .orElseThrow(() -> new TenantNotFoundException(id.toString()));
    }
}
