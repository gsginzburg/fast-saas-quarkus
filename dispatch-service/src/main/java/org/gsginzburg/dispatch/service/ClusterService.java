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
import org.gsginzburg.dispatch.domain.model.ClusterStatus;
import org.gsginzburg.dispatch.domain.repository.ClusterRepository;
import org.gsginzburg.shared.dto.PageDto;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ClusterService {

    @Inject ClusterRepository clusterRepository;

    public List<Cluster> getAllClusters() {
        return clusterRepository.queryAll().list();
    }

    public PageDto<Cluster> getClusters(int page, int size) {
        var query = clusterRepository.queryAll();
        long total = query.count();
        List<Cluster> content = query.page(page, size).list();
        return PageDto.<Cluster>builder()
                .content(content)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .pageNumber(page)
                .pageSize(size)
                .build();
    }

    public Cluster getCluster(UUID id) {
        return clusterRepository.findByIdOptional(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + id));
    }

    @Transactional
    public Cluster createCluster(String name, String url, String apiUrl) {
        Cluster cluster = Cluster.builder()
                .name(name)
                .url(url)
                .apiUrl(apiUrl)
                .status(ClusterStatus.ACTIVE)
                .build();
        clusterRepository.persist(cluster);
        return cluster;
    }

    @Transactional
    public Cluster updateCluster(UUID id, String name, String url, String apiUrl) {
        Cluster cluster = clusterRepository.findByIdOptional(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + id));
        cluster.setName(name);
        cluster.setUrl(url);
        cluster.setApiUrl(apiUrl);
        return cluster;
    }

    @Transactional
    public void deleteCluster(UUID id) {
        Cluster cluster = clusterRepository.findByIdOptional(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + id));
        cluster.setStatus(ClusterStatus.INACTIVE);
    }
}
