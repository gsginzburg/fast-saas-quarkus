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
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.config.DispatchConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@ApplicationScoped
public class ClusterManagementClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject DispatchConfig config;

    public void provisionTenant(String clusterApiUrl, String tenantId) {
        String body = "{\"tenantId\":\"" + tenantId + "\"}";
        call(clusterApiUrl + "/api/management/tenants", "POST", body);
        log.info("Provisioned tenant {} on cluster {}", tenantId, clusterApiUrl);
    }

    public void upgradeTenant(String clusterApiUrl, String tenantId) {
        call(clusterApiUrl + "/api/management/tenants/" + tenantId + "/upgrade", "POST", "");
        log.info("Upgraded tenant {} on cluster {}", tenantId, clusterApiUrl);
    }

    public void upgradeAll(String clusterApiUrl) {
        call(clusterApiUrl + "/api/management/tenants/upgrade-all", "POST", "");
        log.info("Triggered upgrade-all on cluster {}", clusterApiUrl);
    }

    private void call(String url, String method, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", config.management().apiKey())
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Cluster management call failed [" + response.statusCode() + "]: " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cluster management call failed: " + e.getMessage(), e);
        }
    }
}
