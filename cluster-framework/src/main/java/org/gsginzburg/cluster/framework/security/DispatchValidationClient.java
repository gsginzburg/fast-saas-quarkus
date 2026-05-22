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

package org.gsginzburg.cluster.framework.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class DispatchValidationClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Inject ClusterConfig config;
    @Inject ObjectMapper objectMapper;

    public ValidatedToken validate(String token) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("token", token));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.dispatchUrl() + "/api/internal/token/validate"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Api-Key", config.dispatchInternalApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                log.debug("Token rejected by dispatch: {}", response.body());
                return null;
            }
            if (response.statusCode() != 200) {
                log.warn("Dispatch validation returned {}: {}", response.statusCode(), response.body());
                return null;
            }

            Map<String, Object> envelope = objectMapper.readValue(response.body(),
                    new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            if (data == null) return null;

            return extractValidatedToken(data);
        } catch (Exception e) {
            log.error("Failed to validate token with dispatch: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ValidatedToken extractValidatedToken(Map<String, Object> data) {
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        Map<String, Object> tenant = (Map<String, Object>) data.get("tenant");
        Map<String, Object> cluster = (Map<String, Object>) data.get("cluster");
        Map<String, Object> claims = (Map<String, Object>) data.get("claims");

        String userId = user != null ? (String) user.get("id") : null;
        String email = user != null ? (String) user.get("email") : null;
        String firstName = user != null ? (String) user.get("firstName") : null;
        String lastName = user != null ? (String) user.get("lastName") : null;
        String userType = user != null ? (String) user.get("userType") : null;

        String tenantId = tenant != null ? (String) tenant.get("id") : null;
        String tenantName = tenant != null ? (String) tenant.get("name") : null;
        String tenantStatus = tenant != null ? (String) tenant.get("status") : null;

        String clusterName = cluster != null ? (String) cluster.get("name") : null;
        String clusterUrl = cluster != null ? (String) cluster.get("url") : null;

        List<String> roles = List.of();
        if (claims != null && claims.get("roles") instanceof List<?> rawRoles) {
            roles = rawRoles.stream().map(Object::toString).toList();
        }

        return new ValidatedToken(userId, email, firstName, lastName, userType,
                tenantId, tenantName, tenantStatus, clusterName, clusterUrl, roles);
    }
}
