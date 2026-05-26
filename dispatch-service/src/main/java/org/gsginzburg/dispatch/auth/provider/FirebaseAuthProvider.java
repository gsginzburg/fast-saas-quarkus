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

package org.gsginzburg.dispatch.auth.provider;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.config.DispatchConfig;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class FirebaseAuthProvider implements ExternalAuthProvider {

    @Inject
    DispatchConfig config;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private String projectId;

    @PostConstruct
    void init() {
        projectId = config.auth().firebase().projectId()
                .filter(s -> !s.isBlank())
                .orElse(null);
        if (projectId == null) return;

        String jwksUrl = config.auth().firebase().jwksUrlOverride()
                .filter(s -> !s.isBlank())
                .orElse("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");

        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .<SecurityContext>create(URI.create(jwksUrl).toURL())
                    .build();

            String expectedIss = "https://securetoken.google.com/" + projectId;
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    new JWTClaimsSet.Builder()
                            .issuer(expectedIss)
                            .audience(List.of(projectId))
                            .build(),
                    new HashSet<>(Set.of("sub", "exp", "iat"))));
            this.jwtProcessor = processor;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Firebase JWKS URL: " + jwksUrl, e);
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials;
                String serviceAccountJson = config.auth().firebase().serviceAccountJson()
                        .filter(s -> !s.isBlank())
                        .orElse(null);
                if (serviceAccountJson != null) {
                    byte[] decoded = Base64.getDecoder().decode(serviceAccountJson);
                    credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
                } else {
                    credentials = GoogleCredentials.getApplicationDefault();
                }
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Firebase Admin SDK", e);
        }

        log.info("FirebaseAuthProvider initialized for project '{}'", projectId);
    }

    @Override
    public String getProviderId() {
        return "firebase";
    }

    @Override
    public boolean isEnabled() {
        return projectId != null;
    }

    @Override
    public String verifyTokenAndGetEmail(String externalToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(externalToken, null);
            return (String) claims.getClaim("email");
        } catch (BadJOSEException | ParseException | JOSEException e) {
            log.warn("Firebase token validation failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void provisionUser(String email, String password, String firstName, String lastName) {
        try {
            FirebaseAuth.getInstance().createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(firstName + " " + lastName)
                    .setEmailVerified(true));
            log.info("Provisioned Firebase user '{}'", email);
        } catch (FirebaseAuthException e) {
            log.error("Failed to provision Firebase user '{}': {}", email, e.getMessage());
            throw new WebApplicationException("Failed to provision user in Firebase: " + e.getMessage(), 500);
        }
    }

    @Override
    public void deprovisionUser(String email) {
        try {
            UserRecord record = FirebaseAuth.getInstance().getUserByEmail(email);
            FirebaseAuth.getInstance().deleteUser(record.getUid());
            log.info("Deprovisioned Firebase user '{}'", email);
        } catch (FirebaseAuthException e) {
            if ("USER_NOT_FOUND".equals(e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "")) {
                log.warn("Firebase user '{}' not found during deprovision — ignoring", email);
                return;
            }
            log.error("Failed to deprovision Firebase user '{}': {}", email, e.getMessage());
            throw new WebApplicationException("Failed to deprovision user from Firebase: " + e.getMessage(), 500);
        }
    }
}
