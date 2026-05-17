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

package org.gsginzburg.cluster.sample.client;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.shared.client.DispatchClient;
import org.gsginzburg.shared.dto.ApiResponse;
import org.gsginzburg.shared.dto.ClusterInfo;
import org.gsginzburg.shared.dto.ClusterTenantInfo;
import org.gsginzburg.shared.dto.ClusterUserInfo;
import org.gsginzburg.shared.security.JwtClaims;

@Slf4j
@ApplicationScoped
public class DispatchClientImpl implements DispatchClient {

    @Inject ClusterConfig clusterConfig;
    @Inject SecurityIdentity securityIdentity;

    @Override
    public ClusterTenantInfo getTenantInfo(String tenantId) {
        return get("/api/dispatch/tenant/" + tenantId,
                new GenericType<ApiResponse<ClusterTenantInfo>>() {}).data();
    }

    @Override
    public ClusterUserInfo getUserInfo(String userId) {
        return get("/api/dispatch/user/" + userId,
                new GenericType<ApiResponse<ClusterUserInfo>>() {}).data();
    }

    @Override
    public ClusterInfo getClusterInfo(String clusterId) {
        return get("/api/dispatch/cluster/" + clusterId,
                new GenericType<ApiResponse<ClusterInfo>>() {}).data();
    }

    private <T> T get(String path, GenericType<T> type) {
        try (Client client = ClientBuilder.newClient()) {
            return client.target(clusterConfig.dispatchUrl())
                    .path(path)
                    .request()
                    .header("Authorization", "Bearer " + currentToken())
                    .get(type);
        }
    }

    private String currentToken() {
        JwtClaims claims = securityIdentity.getAttribute("claims");
        return claims != null ? claims.sub() : "";
    }
}
