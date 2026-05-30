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

package org.gsginzburg.dispatch.config;

import io.agroal.api.AgroalPoolInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
public class DispatchSchemaInterceptor implements AgroalPoolInterceptor {

    @Inject
    DispatchConfig config;

    @Override
    public void onConnectionAcquire(Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO \"" + config.schema() + "\"");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set search_path to schema: " + config.schema(), e);
        }
    }

    @Override
    public void onConnectionReturn(Connection connection) {
    }
}
