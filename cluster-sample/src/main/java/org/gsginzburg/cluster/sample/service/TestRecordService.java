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

package org.gsginzburg.cluster.sample.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.cluster.sample.domain.model.TestRecord;
import org.gsginzburg.cluster.sample.domain.repository.TestRecordRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TestRecordService {

    @Inject TestRecordRepository testRecordRepository;

    public List<TestRecord> getAll() {
        return testRecordRepository.listAll();
    }

    @Transactional
    public TestRecord create(String name, String description, Integer value) {
        TestRecord record = TestRecord.builder()
                .name(name)
                .description(description)
                .value(value)
                .build();
        testRecordRepository.persist(record);
        return record;
    }

    @Transactional
    public void delete(UUID id) {
        testRecordRepository.deleteById(id);
    }
}
