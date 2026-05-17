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

/**
 * SPI for plugging in external authentication providers (Cognito, Firebase, etc.).
 * Implement and register as a CDI bean to enable external auth.
 */
public interface ExternalAuthProvider {
    /** Provider identifier, e.g. "cognito" or "firebase" */
    String getProviderId();

    /** Verify the external token and return the user's email if valid, null otherwise */
    String verifyTokenAndGetEmail(String externalToken);

    /** Return true if this provider is configured and should be used */
    boolean isEnabled();
}
