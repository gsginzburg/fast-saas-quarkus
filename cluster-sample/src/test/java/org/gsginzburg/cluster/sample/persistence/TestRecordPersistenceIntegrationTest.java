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

package org.gsginzburg.cluster.sample.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.cluster.framework.datasource.TenantContext;
import org.gsginzburg.cluster.framework.datasource.TenantContextHolder;
import org.gsginzburg.cluster.framework.datasource.TenantShardCache;
import org.gsginzburg.cluster.framework.management.TenantMigrationService;
import org.gsginzburg.cluster.sample.AbstractClusterSampleIntegrationTest;
import org.gsginzburg.cluster.sample.domain.model.TestRecord;
import org.gsginzburg.cluster.sample.domain.repository.TestRecordRepository;
import org.gsginzburg.cluster.sample.service.TestRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TestRecordPersistenceIntegrationTest extends AbstractClusterSampleIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/cluster-sample-test";
    private static final String DB_USER  = "cluster-sample-test";
    private static final String DB_PASS  = "cluster-sample-test";

    @Inject TenantMigrationService   tenantMigrationService;
    @Inject TenantShardCache         tenantShardCache;
    @Inject TenantContextHolder      tenantContextHolder;
    @Inject TestRecordRepository     testRecordRepository;
    @Inject TestRecordService        testRecordService;
    @Inject RequestContextController requestContextController;

    private String tenantId;

    @BeforeEach
    void createTenantSchemaAndSetContext() throws Exception {
        requestContextController.activate();
        tenantId = UUID.randomUUID().toString();
        tenantMigrationService.createTenant(tenantId, "shard-1");
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        tenantContextHolder.set(ctx);
    }

    @AfterEach
    void dropTenantSchemaAndClearContext() {
        tenantContextHolder.clear();
        dropSchemaIfExists(tenantId);
        dropSchemaIfExists("archived-" + tenantId);
        tenantShardCache.removeTenant(tenantId);
        requestContextController.deactivate();
    }

    @Test
    void save_persistsRecordAndCanBeLoadedById() throws Exception {
        UUID id = persistRecord("Alpha", "first record", 1);

        TestRecord loaded = findById(id);
        assertThat(loaded.getName()).isEqualTo("Alpha");
        assertThat(loaded.getDescription()).isEqualTo("first record");
        assertThat(loaded.getValue()).isEqualTo(1);

        assertExistsInDb(id, "Alpha", "first record", 1);
    }

    @Test
    void save_allFieldsWrittenToCorrectTenantSchemaInPostgres() throws Exception {
        UUID id = persistRecord("BetaRecord", "db-verify", 99);

        assertExistsInDb(id, "BetaRecord", "db-verify", 99);
    }

    @Test
    void findAll_returnsAllSavedRecords() throws Exception {
        persistRecord("R1", null, 10);
        persistRecord("R2", null, 20);
        persistRecord("R3", null, 30);

        List<TestRecord> all = listAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(TestRecord::getName)
                .containsExactlyInAnyOrder("R1", "R2", "R3");

        assertRowCountInDb(3);
    }

    @Test
    void deleteById_removesRecordFromDb() throws Exception {
        UUID id = persistRecord("ToDelete", null, 0);

        assertExistsInDb(id, "ToDelete", null, 0);

        deleteInTransaction(id);

        assertThat(testRecordRepository.findByIdOptional(id)).isEmpty();
        assertRowCountInDb(0);
    }

    @Test
    void service_create_persistsViaServiceAndVerifiableByRepository() throws Exception {
        TestRecord created = testRecordService.create("ServiceRecord", "via service", 55);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("ServiceRecord");
        assertThat(created.getDescription()).isEqualTo("via service");
        assertThat(created.getValue()).isEqualTo(55);

        TestRecord stored = testRecordRepository.findByIdOptional(created.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("ServiceRecord");

        assertExistsInDb(created.getId(), "ServiceRecord", "via service", 55);
    }

    @Test
    void service_getAll_returnsAllPersistedRecords() throws Exception {
        testRecordService.create("SA", "a", 1);
        testRecordService.create("SB", "b", 2);

        List<TestRecord> all = testRecordService.getAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(TestRecord::getName)
                .containsExactlyInAnyOrder("SA", "SB");

        assertRowCountInDb(2);
    }

    @Test
    void service_delete_removesRecordFromDb() throws Exception {
        TestRecord created = testRecordService.create("ToGo", null, 0);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("ToGo");
        assertThat(created.getDescription()).isNull();
        assertThat(created.getValue()).isEqualTo(0);
        assertExistsInDb(created.getId(), "ToGo", null, 0);

        testRecordService.delete(created.getId());

        assertThat(testRecordRepository.findByIdOptional(created.getId())).isEmpty();
        assertRowCountInDb(0);
    }

    @Test
    void data_isIsolatedToTenantSchema() throws Exception {
        TestRecord created = testRecordService.create("Isolated", null, 7);

        assertExistsInDb(created.getId(), "Isolated", null, 7);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static TestRecord record(String name, String description, Integer value) {
        return TestRecord.builder().name(name).description(description).value(value).build();
    }

    @Transactional
    UUID persistRecord(String name, String description, Integer value) {
        TestRecord r = record(name, description, value);
        testRecordRepository.persist(r);
        return r.getId();
    }

    @Transactional
    TestRecord findById(UUID id) {
        return testRecordRepository.findByIdOptional(id).orElseThrow();
    }

    @Transactional
    List<TestRecord> listAll() {
        return testRecordRepository.listAll();
    }

    @Transactional
    void deleteInTransaction(UUID id) {
        testRecordRepository.deleteById(id);
    }

    private void assertExistsInDb(UUID id, String name, String description, Integer value) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT name, description, value FROM \"" + tenantId + "\".test WHERE id = '" + id + "'");
            assertThat(rs.next()).as("Row %s must exist in cluster-sample-test / schema %s", id, tenantId).isTrue();
            assertThat(rs.getString("name")).isEqualTo(name);
            assertThat(rs.getString("description")).isEqualTo(description);
            assertThat(rs.getInt("value")).isEqualTo(value);
        }
    }

    private void assertRowCountInDb(int expected) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM \"" + tenantId + "\".test");
            rs.next();
            assertThat(rs.getInt(1)).as("Row count in cluster-sample-test / schema %s", tenantId).isEqualTo(expected);
        }
    }

    private void dropSchemaIfExists(String schemaName) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
        } catch (Exception e) {
            System.err.println("[Test cleanup] Failed to drop schema " + schemaName + ": " + e.getMessage());
        }
    }
}
