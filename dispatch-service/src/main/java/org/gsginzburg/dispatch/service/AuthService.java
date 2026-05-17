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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.auth.provider.ExternalAuthProvider;
import org.gsginzburg.dispatch.config.DispatchConfig;
import org.gsginzburg.dispatch.domain.dto.LoginRequest;
import org.gsginzburg.dispatch.domain.dto.LoginResponse;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.RefreshToken;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.RefreshTokenRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.JwtService;
import org.gsginzburg.shared.security.TokenType;
import org.gsginzburg.shared.security.UserType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class AuthService {

    @Inject AppUserRepository userRepository;
    @Inject TenantUserRepository tenantUserRepository;
    @Inject TenantRepository tenantRepository;
    @Inject RefreshTokenRepository refreshTokenRepository;
    @Inject JwtService jwtService;
    @Inject Instance<ExternalAuthProvider> externalAuthProviders;
    @Inject DispatchConfig config;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmailAndStatus(request.email(), UserStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!BcryptUtil.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithExternalToken(String externalToken, String providerName) {
        ExternalAuthProvider provider = externalAuthProviders.stream()
                .filter(p -> p.getProviderId().equals(providerName) && p.isEnabled())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Auth provider not available: " + providerName));

        String email = provider.verifyTokenAndGetEmail(externalToken);
        if (email == null) throw new RuntimeException("Invalid external token");

        AppUser user = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(AppUser user) {
        long expiry;
        TokenType tokenType;
        String clusterUrl = null;
        String tenantId = null;
        List<String> roles;

        if (user.getUserType() == UserType.BACKOFFICE) {
            expiry = config.jwt().backofficeTokenExpirySeconds();
            tokenType = TokenType.BACKOFFICE;
            roles = List.of("BACKOFFICE");
        } else {
            expiry = config.jwt().exchangeTokenExpirySeconds();
            tokenType = TokenType.TENANT_EXCHANGE;
            roles = List.of("TENANT");
            List<TenantUser> tenantUsers = tenantUserRepository.findActiveByUserId(user.getId());
            if (!tenantUsers.isEmpty()) {
                TenantUser tu = tenantUsers.get(0);
                tenantId = tu.getTenantId().toString();
                Tenant tenant = tenantRepository.findByIdOptional(tu.getTenantId()).orElseThrow();
                clusterUrl = tenant.getCluster().getUrl();
                roles = List.of(tu.getRole().name());
            }
        }

        Instant now = Instant.now();
        JwtClaims claims = JwtClaims.builder()
                .sub(user.getId().toString())
                .type(tokenType)
                .email(user.getEmail())
                .tenantId(tenantId)
                .clusterUrl(clusterUrl)
                .roles(roles)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .build();

        String accessToken = jwtService.generateToken(claims);
        String rawRefreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), rawRefreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(expiry)
                .userType(user.getUserType().name())
                .clusterUrl(clusterUrl)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndNotRevoked(hash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired refresh token"));

        if (stored.getExpiresAt().toInstant().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.persist(stored);

        AppUser user = userRepository.findByIdOptional(stored.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return buildLoginResponse(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void saveRefreshToken(UUID userId, String rawToken) {
        refreshTokenRepository.persist(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .revoked(false)
                .build());
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
