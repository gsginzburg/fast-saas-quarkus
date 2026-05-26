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

/**
 * Test-only external auth provider. Always enabled, responds to provider ID "test-provider".
 * Accepts the literal token "valid-test-token" and resolves it to "mock-external@test.com".
 * Used in integration tests to exercise the external login flow without real provider credentials.
 */
@ApplicationScoped
public class MockExternalAuthProvider implements ExternalAuthProvider {

    static final String PROVIDER_ID = "test-provider";
    static final String VALID_TOKEN = "valid-test-token";
    static final String RESOLVED_EMAIL = "mock-external@test.com";

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String verifyTokenAndGetEmail(String externalToken) {
        return VALID_TOKEN.equals(externalToken) ? RESOLVED_EMAIL : null;
    }

    @Override
    public void provisionUser(String email, String password, String firstName, String lastName) {
        // no-op in tests
    }

    @Override
    public void deprovisionUser(String email) {
        // no-op in tests
    }
}
