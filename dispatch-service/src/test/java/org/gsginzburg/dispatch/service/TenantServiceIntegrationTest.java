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

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.AbstractIntegrationTest;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantStatus;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.TenantUserId;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.dto.PageDto;
import org.gsginzburg.shared.security.UserRole;
import org.gsginzburg.shared.security.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class TenantServiceIntegrationTest extends AbstractIntegrationTest {

    @Inject TenantService tenantService;
    @Inject ClusterService clusterService;
    @Inject TenantRepository tenantRepository;
    @Inject AppUserRepository appUserRepository;
    @Inject TenantUserRepository tenantUserRepository;
    @Inject AgroalDataSource dataSource;

    private UUID clusterId;

    @BeforeEach
    void setupCluster() {
        Cluster cluster = clusterService.createCluster(
                "Test Cluster " + UUID.randomUUID(), "https://test.cluster.internal", "");
        clusterId = cluster.getId();
    }

    @AfterEach
    void cleanupDb() {
        AbstractIntegrationTest.DbProvisioner.runPsql("dispatch-test",
                "TRUNCATE dispatch.tenant_user, dispatch.tenant, dispatch.app_user, dispatch.cluster CASCADE");
    }

    @Test
    void createTenant_persistsAllFieldsToDb() {
        Tenant tenant = createTenant("Acme Corp");

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenant.getName()).isEqualTo("Acme Corp");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getCluster().getId()).isEqualTo(clusterId);

        Tenant stored = tenantRepository.findByIdOptional(tenant.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant WHERE id = ?::uuid AND name = ? AND status = 'ACTIVE' AND cluster_id = ?::uuid",
                tenant.getId().toString(), "Acme Corp", clusterId.toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void getTenant_returnsCorrectDataWithClusterInfo() {
        Tenant created = createTenant("Globex Corp");

        Tenant fetched = tenantService.getTenant(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getName()).isEqualTo("Globex Corp");
        assertThat(fetched.getCluster().getId()).isEqualTo(clusterId);
        assertThat(fetched.getCluster().getUrl()).isEqualTo("https://test.cluster.internal");
    }

    @Test
    void getTenants_paginationWorks() {
        for (int i = 1; i <= 4; i++) createTenant("Tenant Paged " + i);

        PageDto<Tenant> page = tenantService.getTenants(0, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void updateTenantStatus_modifiesStatusInDb() {
        Tenant created = createTenant("Status Corp");

        tenantService.updateTenantStatus(created.getId(), TenantStatus.INACTIVE);

        Tenant stored = tenantRepository.findByIdOptional(created.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.INACTIVE);

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant WHERE id = ?::uuid AND status = 'INACTIVE'",
                created.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void assignCluster_updatesClusterIdInDb() {
        Tenant created = createTenant("Mobile Corp");
        Cluster clusterB = clusterService.createCluster(
                "Cluster B " + UUID.randomUUID(), "https://b.cluster.internal", "");

        tenantService.assignCluster(created.getId(), clusterB.getId());

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant WHERE id = ?::uuid AND cluster_id = ?::uuid",
                created.getId().toString(), clusterB.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void deleteTenant_setsStatusArchivedInDb() {
        Tenant created = createTenant("Archived Corp");

        tenantService.deleteTenant(created.getId());

        Tenant stored = tenantRepository.findByIdOptional(created.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.ARCHIVED);

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant WHERE id = ?::uuid AND status = 'ARCHIVED'",
                created.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void assignUser_createsTenantUserRowInDb() {
        Tenant tenant = createTenant("User Corp");
        AppUser user = createUser("john@test.local");

        tenantService.assignUser(tenant.getId(), user.getId(), UserRole.USER);

        TenantUser tu = tenantUserRepository.findByIdOptional(
                new TenantUserId(tenant.getId(), user.getId())).orElseThrow();
        assertThat(tu.getRole()).isEqualTo(UserRole.USER);
        assertThat(tu.getStatus()).isEqualTo(UserStatus.ACTIVE);

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant_user WHERE tenant_id = ?::uuid AND user_id = ?::uuid AND role = 'USER' AND status = 'ACTIVE'",
                tenant.getId().toString(), user.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void removeUser_setsUserStatusInactiveInDb() {
        Tenant tenant = createTenant("Leave Corp");
        AppUser user = createUser("jane@test.local");

        tenantService.assignUser(tenant.getId(), user.getId(), UserRole.ADMIN);
        tenantService.removeUser(tenant.getId(), user.getId());

        TenantUser tu = tenantUserRepository.findByIdOptional(
                new TenantUserId(tenant.getId(), user.getId())).orElseThrow();
        assertThat(tu.getStatus()).isEqualTo(UserStatus.INACTIVE);

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.tenant_user WHERE tenant_id = ?::uuid AND user_id = ?::uuid AND status = 'INACTIVE'",
                tenant.getId().toString(), user.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void getTenantUsers_returnsAssignedMembers() {
        Tenant tenant = createTenant("Team Corp");
        AppUser u1 = createUser("member1@test.local");
        AppUser u2 = createUser("member2@test.local");

        tenantService.assignUser(tenant.getId(), u1.getId(), UserRole.ADMIN);
        tenantService.assignUser(tenant.getId(), u2.getId(), UserRole.USER);

        List<TenantUser> members = tenantService.getTenantUsers(tenant.getId());
        assertThat(members).hasSize(2);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Tenant createTenant(String name) {
        return tenantService.createTenant(name, clusterId);
    }

    @Transactional
    AppUser createUser(String email) {
        AppUser user = AppUser.builder()
                .email(email)
                .userType(UserType.TENANT)
                .status(UserStatus.ACTIVE)
                .build();
        appUserRepository.persist(user);
        return user;
    }

    private int queryForInt(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
