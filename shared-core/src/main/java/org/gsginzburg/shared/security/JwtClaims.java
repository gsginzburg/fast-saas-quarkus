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

package org.gsginzburg.shared.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Canonical representation of the claims carried in every platform JWT.
 * Fields not present in a given token type will be null.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record JwtClaims(
        String sub,
        String iss,
        TokenType type,
        String tenantId,
        String clusterUrl,
        String clusterId,
        String email,
        List<String> roles,
        Instant issuedAt,
        Instant expiresAt
) {}
