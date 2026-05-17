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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gsginzburg.dispatch.config.DispatchConfig;

/**
 * Stub for AWS Cognito authentication.
 * To fully implement: fetch JWKS from
 * https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
 * and validate the JWT locally using the AWS SDK.
 */
@Slf4j
@ApplicationScoped
public class CognitoAuthProvider implements ExternalAuthProvider {

    @Inject
    DispatchConfig config;

    @Override
    public String getProviderId() {
        return "cognito";
    }

    @Override
    public String verifyTokenAndGetEmail(String externalToken) {
        log.warn("Cognito auth provider is not fully implemented — returning null");
        return null;
    }

    @Override
    public boolean isEnabled() {
        return config.auth().cognito().userPoolId()
                .filter(s -> !s.isBlank())
                .isPresent();
    }
}
