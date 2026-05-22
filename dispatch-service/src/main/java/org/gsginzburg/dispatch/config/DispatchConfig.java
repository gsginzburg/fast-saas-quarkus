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

package org.gsginzburg.dispatch.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "dispatch")
public interface DispatchConfig {

    @WithDefault("dispatch")
    String schema();

    Jwt jwt();
    Cors cors();
    Auth auth();
    Management management();

    Internal internal();

    interface Internal {
        @WithDefault("change-me-internal-api-key")
        String apiKey();
    }

    interface Jwt {
        String secret();

        @WithDefault("dispatch")
        String issuer();

        @WithDefault("28800")
        long backofficeTokenExpirySeconds();

        @WithDefault("28800")
        long userTokenExpirySeconds();
    }

    interface Cors {
        @WithDefault("http://localhost:4200")
        String allowedOrigins();
    }

    interface Management {
        @WithDefault("change-me-in-production")
        String apiKey();
    }

    interface Auth {
        @WithDefault("native")
        String provider();

        Cognito cognito();
        Firebase firebase();

        interface Cognito {
            Optional<String> userPoolId();

            @WithDefault("us-east-1")
            String region();
        }

        interface Firebase {
            Optional<String> projectId();
        }
    }
}
