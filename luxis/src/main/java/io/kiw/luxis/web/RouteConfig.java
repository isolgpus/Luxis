package io.kiw.luxis.web;

import io.kiw.luxis.web.openapi.OpenApiMetadata;

import java.util.Optional;
import java.util.OptionalInt;

public record RouteConfig(OptionalInt timeoutInMillis, Optional<OpenApiMetadata> openApiMetadata) {

    public RouteConfig(final OptionalInt timeoutInMillis) {
        this(timeoutInMillis, Optional.empty());
    }
}
