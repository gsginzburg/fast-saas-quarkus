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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class CognitoAuthProvider implements ExternalAuthProvider {

    @Inject
    DispatchConfig config;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private CognitoIdentityProviderClient cognitoClient;
    private String userPoolId;

    @PostConstruct
    void init() {
        userPoolId = config.auth().cognito().userPoolId()
                .filter(s -> !s.isBlank())
                .orElse(null);
        if (userPoolId == null) return;

        String region = config.auth().cognito().region();
        String jwksUrl = config.auth().cognito().jwksUrlOverride()
                .filter(s -> !s.isBlank())
                .orElse("https://cognito-idp." + region + ".amazonaws.com/" + userPoolId + "/.well-known/jwks.json");

        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .<SecurityContext>create(URI.create(jwksUrl).toURL())
                    .build();

            String expectedIssuer = "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    new JWTClaimsSet.Builder().issuer(expectedIssuer).build(),
                    new HashSet<>(Set.of("sub", "exp", "iat"))));
            this.jwtProcessor = processor;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Cognito JWKS URL: " + jwksUrl, e);
        }

        cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();

        log.info("CognitoAuthProvider initialized for user pool '{}' (region '{}')", userPoolId, region);
    }

    @Override
    public String getProviderId() {
        return "cognito";
    }

    @Override
    public boolean isEnabled() {
        return userPoolId != null;
    }

    @Override
    public String verifyTokenAndGetEmail(String externalToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(externalToken, null);
            return (String) claims.getClaim("email");
        } catch (BadJOSEException | ParseException | JOSEException e) {
            log.warn("Cognito token validation failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void provisionUser(String email, String password, String firstName, String lastName) {
        try {
            cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("given_name").value(firstName).build(),
                            AttributeType.builder().name("family_name").value(lastName).build()
                    )
                    .build());

            cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build());

            log.info("Provisioned Cognito user '{}'", email);
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to provision Cognito user '{}': {}", email, e.getMessage());
            throw new WebApplicationException("Failed to provision user in Cognito: " + e.awsErrorDetails().errorMessage(), 500);
        }
    }

    @Override
    public void deprovisionUser(String email) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build());
            log.info("Deprovisioned Cognito user '{}'", email);
        } catch (CognitoIdentityProviderException e) {
            if ("UserNotFoundException".equals(e.awsErrorDetails().errorCode())) {
                log.warn("Cognito user '{}' not found during deprovision — ignoring", email);
                return;
            }
            log.error("Failed to deprovision Cognito user '{}': {}", email, e.getMessage());
            throw new WebApplicationException("Failed to deprovision user from Cognito: " + e.awsErrorDetails().errorMessage(), 500);
        }
    }
}
