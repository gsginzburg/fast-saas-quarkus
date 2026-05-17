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
import org.gsginzburg.dispatch.AbstractIntegrationTest;
import org.gsginzburg.dispatch.domain.model.Cluster;
import org.gsginzburg.dispatch.domain.model.ClusterStatus;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.shared.dto.PageDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
public class ClusterServiceIntegrationTest extends AbstractIntegrationTest {

    @Inject ClusterService clusterService;
    @Inject ClusterRepository clusterRepository;
    @Inject AgroalDataSource dataSource;

    @Test
    void createCluster_persistsAllFieldsToDb() {
        Cluster cluster = createCluster("Alpha Cluster", "https://alpha.cluster.internal");

        assertThat(cluster.getId()).isNotNull();
        assertThat(cluster.getName()).isEqualTo("Alpha Cluster");
        assertThat(cluster.getUrl()).isEqualTo("https://alpha.cluster.internal");
        assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.ACTIVE);

        Cluster stored = clusterRepository.findByIdOptional(cluster.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.cluster WHERE id = ?::uuid AND name = ? AND url = ? AND status = 'ACTIVE'",
                cluster.getId().toString(), "Alpha Cluster", "https://alpha.cluster.internal");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void getCluster_returnsCorrectData() {
        Cluster created = createCluster("Beta Cluster", "https://beta.cluster.internal");

        Cluster fetched = clusterService.getCluster(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getName()).isEqualTo("Beta Cluster");
        assertThat(fetched.getStatus()).isEqualTo(ClusterStatus.ACTIVE);
    }

    @Test
    void getAllClusters_returnsSortedByName() {
        createCluster("Zebra Cluster", "https://zebra.internal");
        createCluster("Apple Cluster", "https://apple.internal");
        createCluster("Mango Cluster", "https://mango.internal");

        List<Cluster> all = clusterService.getAllClusters();
        List<String> names = all.stream().map(Cluster::getName)
                .filter(n -> n.endsWith("Cluster")).toList();

        assertThat(names).containsExactly("Apple Cluster", "Mango Cluster", "Zebra Cluster");
    }

    @Test
    void getClusters_paginationWorks() {
        for (int i = 1; i <= 5; i++) {
            createCluster("Page Cluster " + i, "https://page" + i + ".internal");
        }

        PageDto<Cluster> page0 = clusterService.getClusters(0, 3);
        PageDto<Cluster> page1 = clusterService.getClusters(1, 3);

        assertThat(page0.content()).hasSize(3);
        assertThat(page0.totalPages()).isGreaterThanOrEqualTo(2);
        assertThat(page1.content()).isNotEmpty();
    }

    @Test
    void updateCluster_modifiesNameAndUrlInDb() {
        Cluster created = createCluster("Old Name", "https://old.internal");

        clusterService.updateCluster(created.getId(), "New Name", "https://new.internal");

        Cluster stored = clusterRepository.findByIdOptional(created.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("New Name");
        assertThat(stored.getUrl()).isEqualTo("https://new.internal");

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.cluster WHERE id = ?::uuid AND name = 'New Name' AND url = 'https://new.internal'",
                created.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void deleteCluster_setsStatusInactiveInDb() {
        Cluster created = createCluster("Doomed Cluster", "https://doomed.internal");

        clusterService.deleteCluster(created.getId());

        Cluster stored = clusterRepository.findByIdOptional(created.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ClusterStatus.INACTIVE);

        int count = queryForInt(
                "SELECT COUNT(*) FROM dispatch.cluster WHERE id = ?::uuid AND status = 'INACTIVE'",
                created.getId().toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void createCluster_duplicateName_throwsException() {
        createCluster("Unique Cluster", "https://unique.internal");

        assertThatThrownBy(() ->
                clusterService.createCluster("Unique Cluster", "https://other.internal"))
                .isInstanceOf(Exception.class);
    }

    @AfterEach
    void cleanupDb() {
        AbstractIntegrationTest.DbProvisioner.runPsql("dispatch-test",
                "TRUNCATE dispatch.cluster CASCADE");
    }

    private Cluster createCluster(String name, String url) {
        return clusterService.createCluster(name, url);
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
