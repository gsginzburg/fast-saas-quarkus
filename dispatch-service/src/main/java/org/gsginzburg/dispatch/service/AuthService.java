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
import org.gsginzburg.dispatch.domain.dto.ScopedTokenResponse;
import org.gsginzburg.dispatch.domain.model.AppUser;
import org.gsginzburg.dispatch.domain.model.RefreshToken;
import org.gsginzburg.dispatch.domain.model.Tenant;
import org.gsginzburg.dispatch.domain.model.TenantUser;
import org.gsginzburg.dispatch.domain.model.UserStatus;
import org.gsginzburg.dispatch.domain.repository.AppUserRepository;
import org.gsginzburg.dispatch.domain.repository.RefreshTokenRepository;
import org.gsginzburg.dispatch.domain.repository.TenantRepository;
import org.gsginzburg.dispatch.domain.repository.TenantUserRepository;
import org.gsginzburg.shared.exception.DispatchException;
import org.gsginzburg.shared.security.JwtClaims;
import org.gsginzburg.shared.security.JwtService;
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
                .orElseThrow(() -> {
                    log.warn("Authentication failed: no active user with email '{}'", request.email());
                    return new DispatchException("Invalid credentials", 401);
                });

        if (!BcryptUtil.matches(request.password(), user.getPasswordHash())) {
            log.warn("Authentication failed: wrong password for '{}'", request.email());
            throw new DispatchException("Invalid credentials", 401);
        }

        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithExternalToken(String externalToken, String providerName) {
        ExternalAuthProvider provider = externalAuthProviders.stream()
                .filter(p -> p.getProviderId().equals(providerName) && p.isEnabled())
                .findFirst()
                .orElseThrow(() -> new DispatchException("Auth provider not available: " + providerName, 400));

        String email = provider.verifyTokenAndGetEmail(externalToken);
        if (email == null) {
            log.warn("Authentication failed: external token rejected by provider '{}'", providerName);
            throw new DispatchException("Invalid external token", 401);
        }

        AppUser user = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: no active user for external identity '{}' (provider '{}')", email, providerName);
                    return new DispatchException("User not found for external identity: " + email, 404);
                });

        if (!providerName.equals(user.getAuthProvider())) {
            log.warn("Authentication failed: user '{}' authProvider='{}' does not match requested provider '{}'",
                    email, user.getAuthProvider(), providerName);
            throw new DispatchException("This account is not configured for " + providerName + " authentication", 401);
        }

        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(AppUser user) {
        long expiry;
        String clusterUrl = null;
        String clusterId = null;
        String tenantId = null;
        String tenantName = null;
        String tenantStatus = null;
        String clusterName = null;
        List<String> roles;

        if (user.getUserType() == UserType.BACKOFFICE) {
            expiry = config.jwt().backofficeTokenExpirySeconds();
            roles = List.of("BACKOFFICE");
        } else {
            expiry = config.jwt().userTokenExpirySeconds();
            roles = List.of("USER");
            List<TenantUser> tenantUsers = tenantUserRepository.findActiveByUserId(user.getId());
            if (!tenantUsers.isEmpty()) {
                TenantUser tu = tenantUsers.get(0);
                tenantId = tu.getTenantId().toString();
                Tenant tenant = tenantRepository.findByIdWithCluster(tu.getTenantId()).orElseThrow();
                clusterUrl = tenant.getCluster().getUrl();
                clusterId = tenant.getCluster().getId().toString();
                tenantName = tenant.getName();
                tenantStatus = tenant.getStatus().name();
                clusterName = tenant.getCluster().getName();
                roles = List.of(tu.getRole().name());
            }
        }

        String userName = trim(user.getFirstName()) + " " + trim(user.getLastName());

        Instant now = Instant.now();
        JwtClaims claims = JwtClaims.builder()
                .sub(user.getId().toString())
                .email(user.getEmail())
                .userName(userName.isBlank() ? null : userName.strip())
                .userType(user.getUserType().name())
                .tenantId(tenantId)
                .clusterUrl(clusterUrl)
                .clusterId(clusterId)
                .tenantName(tenantName)
                .tenantStatus(tenantStatus)
                .clusterName(clusterName)
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
                .orElseThrow(() -> {
                    log.warn("Token refresh failed: token not found or already revoked");
                    return new RuntimeException("Invalid or expired refresh token");
                });

        if (stored.getExpiresAt().toInstant().isBefore(Instant.now())) {
            log.warn("Token refresh failed: token expired for user '{}'", stored.getUserId());
            throw new RuntimeException("Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.persist(stored);

        AppUser user = userRepository.findByIdOptional(stored.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return buildLoginResponse(user);
    }

    @Transactional
    public ScopedTokenResponse scopeToTenant(String userId, String tenantId) {
        Tenant tenant = tenantRepository.findByIdWithCluster(UUID.fromString(tenantId))
                .orElseThrow(() -> new DispatchException("Tenant not found", 404));

        long expiry = config.jwt().backofficeTokenExpirySeconds();
        Instant now = Instant.now();

        JwtClaims claims = JwtClaims.builder()
                .sub(userId)
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .tenantStatus(tenant.getStatus().name())
                .clusterUrl(tenant.getCluster().getUrl())
                .clusterId(tenant.getCluster().getId().toString())
                .clusterName(tenant.getCluster().getName())
                .roles(List.of("BACKOFFICE"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .build();

        String token = jwtService.generateToken(claims);
        return new ScopedTokenResponse(token, expiry);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    private String trim(String s) {
        return s != null ? s.trim() : "";
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
