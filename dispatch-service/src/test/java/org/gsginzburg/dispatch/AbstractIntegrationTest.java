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

package org.gsginzburg.dispatch;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

@QuarkusTestResource(AbstractIntegrationTest.DbProvisioner.class)
public abstract class AbstractIntegrationTest {

    public static class DbProvisioner implements QuarkusTestResourceLifecycleManager {

        @Override
        public Map<String, String> start() {
            runPsql("postgres", "DROP DATABASE IF EXISTS \"dispatch-test\" WITH (FORCE)");
            runPsql("postgres", "DROP USER IF EXISTS \"dispatch-test\"");
            runPsql("postgres", "CREATE USER \"dispatch-test\" WITH PASSWORD 'dispatch-test'");
            runPsql("postgres", "CREATE DATABASE \"dispatch-test\" OWNER \"dispatch-test\"");
            runPsql("postgres", "GRANT ALL PRIVILEGES ON DATABASE \"dispatch-test\" TO \"dispatch-test\"");
            return Map.of();
        }

        @Override
        public void stop() {
            runPsql("postgres", "DROP DATABASE IF EXISTS \"dispatch-test\" WITH (FORCE)");
            runPsql("postgres", "DROP USER IF EXISTS \"dispatch-test\"");
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
}
