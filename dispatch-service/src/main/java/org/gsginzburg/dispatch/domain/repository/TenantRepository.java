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

package org.gsginzburg.dispatch.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.gsginzburg.dispatch.domain.model.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantRepository implements PanacheRepositoryBase<Tenant, UUID> {

    @Inject EntityManager em;

    public Optional<Tenant> findByName(String name) {
        return em.createQuery(
                "SELECT t FROM Tenant t WHERE t.name = :name", Tenant.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst();
    }

    public List<Tenant> findByClusterId(UUID clusterId) {
        return em.createQuery(
                "SELECT t FROM Tenant t WHERE t.cluster.id = :clusterId", Tenant.class)
                .setParameter("clusterId", clusterId)
                .getResultList();
    }

    public Optional<Tenant> findByIdWithCluster(UUID id) {
        return em.createQuery(
                "SELECT t FROM Tenant t JOIN FETCH t.cluster WHERE t.id = :id", Tenant.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public long countAll() {
        return em.createQuery("SELECT COUNT(t) FROM Tenant t", Long.class)
                .getSingleResult();
    }

    public List<Tenant> findPageWithCluster(int page, int size) {
        return em.createQuery(
                "SELECT t FROM Tenant t JOIN FETCH t.cluster ORDER BY t.name", Tenant.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
