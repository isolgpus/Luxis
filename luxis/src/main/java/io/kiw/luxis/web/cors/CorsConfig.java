package io.kiw.luxis.web.cors;

import java.util.Collections;
import java.util.Set;

public record CorsConfig(Set<String> allowedOrigins, Set<String> allowedMethods,
                         Set<String> allowedHeaders, Set<String> exposedHeaders,
                         boolean allowCredentials, int maxAgeSeconds) {

    public CorsConfig {
        allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
        allowedMethods = Collections.unmodifiableSet(allowedMethods);
        allowedHeaders = Collections.unmodifiableSet(allowedHeaders);
        exposedHeaders = Collections.unmodifiableSet(exposedHeaders);
    }
}
