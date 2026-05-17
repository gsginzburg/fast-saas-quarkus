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

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.shared.dto.PageDto;
import org.gsginzburg.shared.security.UserType;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    @Inject AppUserRepository userRepository;

    public PageDto<AppUser> getUsers(int page, int size) {
        var query = userRepository.queryAll();
        long total = query.count();
        List<AppUser> content = query.page(page, size).list();
        return PageDto.<AppUser>builder()
                .content(content)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .pageNumber(page)
                .pageSize(size)
                .build();
    }

    public AppUser getUser(UUID id) {
        return findUser(id);
    }

    @Transactional
    public AppUser createUser(String email, String password,
                              String firstName, String lastName, UserType userType) {
        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash(BcryptUtil.bcryptHash(password))
                .firstName(firstName)
                .lastName(lastName)
                .userType(userType)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.persist(user);
        return user;
    }

    @Transactional
    public AppUser updateUserStatus(UUID id, UserStatus status) {
        AppUser user = findUser(id);
        user.setStatus(status);
        return user;
    }

    @Transactional
    public void deleteUser(UUID id) {
        findUser(id).setStatus(UserStatus.INACTIVE);
    }

    private AppUser findUser(UUID id) {
        return userRepository.findByIdOptional(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }
}
