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

package org.gsginzburg.cluster.framework;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

@QuarkusTestResource(AbstractClusterIntegrationTest.DbProvisioner.class)
public abstract class AbstractClusterIntegrationTest {

    protected static final String SHARD_JDBC_URL = "jdbc:postgresql://localhost:5432/cluster-fw-test";
    protected static final String SHARD_USER     = "cluster-fw-test";
    protected static final String SHARD_PASS     = "cluster-fw-test";

    public static class DbProvisioner implements QuarkusTestResourceLifecycleManager {

        @Override
        public Map<String, String> start() {
            runPsql("postgres", "DROP DATABASE IF EXISTS \"cluster-fw-test\" WITH (FORCE)");
            runPsql("postgres", "DROP USER IF EXISTS \"cluster-fw-test\"");
            runPsql("postgres", "CREATE USER \"cluster-fw-test\" WITH PASSWORD 'cluster-fw-test'");
            runPsql("postgres", "CREATE DATABASE \"cluster-fw-test\" OWNER \"cluster-fw-test\"");
            runPsql("postgres", "GRANT ALL PRIVILEGES ON DATABASE \"cluster-fw-test\" TO \"cluster-fw-test\"");
            return Map.of();
        }

        @Override
        public void stop() {
            runPsql("postgres", "DROP DATABASE IF EXISTS \"cluster-fw-test\" WITH (FORCE)");
            runPsql("postgres", "DROP USER IF EXISTS \"cluster-fw-test\"");
        }

        public static void runPsql(String database, String sql) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "sudo", "-n", "-u", "postgres", "psql", "-d", database, "-c", sql);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());
                int exit = p.waitFor();
                if (exit != 0) {
                    System.err.println("[DbProvisioner] psql exit=" + exit + ": " + output.trim());
                }
            } catch (Exception e) {
                System.err.println("[DbProvisioner] Failed: " + sql + " — " + e.getMessage());
            }
        }
    }

    protected void dropSchemaIfExists(String schemaName) {
        try (Connection conn = DriverManager.getConnection(SHARD_JDBC_URL, SHARD_USER, SHARD_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
        } catch (Exception e) {
            System.err.println("[Test cleanup] Failed to drop schema " + schemaName + ": " + e.getMessage());
        }
    }

    protected void dropAllTestSchemas() {
        String sql = "SELECT schema_name FROM information_schema.schemata " +
                     "WHERE schema_name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' " +
                     "   OR schema_name LIKE 'archived-%'";
        try (Connection conn = DriverManager.getConnection(SHARD_JDBC_URL, SHARD_USER, SHARD_PASS);
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString(1);
                try (Statement drop = conn.createStatement()) {
                    drop.execute("DROP SCHEMA IF EXISTS \"" + name + "\" CASCADE");
                }
            }
        } catch (Exception e) {
            System.err.println("[Test cleanup] Failed to drop test schemas: " + e.getMessage());
        }
    }
}
