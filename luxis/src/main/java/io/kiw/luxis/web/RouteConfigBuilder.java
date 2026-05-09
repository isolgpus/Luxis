package io.kiw.luxis.web;

import io.kiw.luxis.web.openapi.OpenApiMetadataBuilder;

import java.util.Optional;
import java.util.OptionalInt;

public class RouteConfigBuilder {
    private OptionalInt timeoutInMillis = OptionalInt.empty();
    private OpenApiMetadataBuilder openApiBuilder = null;

    public RouteConfigBuilder() {
    }

    public RouteConfigBuilder timeout(final int timeoutInMillis) {
        this.timeoutInMillis = OptionalInt.of(timeoutInMillis);
        return this;
    }

    public OpenApiMetadataBuilder openApi() {
        if (openApiBuilder == null) {
            openApiBuilder = new OpenApiMetadataBuilder(this);
        }
        return openApiBuilder;
    }

    public RouteConfig build() {
        return new RouteConfig(
                timeoutInMillis,
                openApiBuilder != null ? Optional.of(openApiBuilder.buildMetadata()) : Optional.empty()
        );
    }
}
