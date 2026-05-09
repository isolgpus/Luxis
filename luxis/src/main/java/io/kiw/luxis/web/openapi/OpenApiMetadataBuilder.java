package io.kiw.luxis.web.openapi;

import io.kiw.luxis.web.RouteConfig;
import io.kiw.luxis.web.RouteConfigBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenApiMetadataBuilder {
    private final RouteConfigBuilder parent;
    private String summary;
    private String description;
    private final List<String> tags = new ArrayList<>();
    private final Map<String, String> parameterDescriptions = new LinkedHashMap<>();
    private String responseDescription;
    private boolean hidden = false;

    public OpenApiMetadataBuilder(final RouteConfigBuilder parent) {
        this.parent = parent;
    }

    public OpenApiMetadataBuilder summary(final String summary) {
        this.summary = summary;
        return this;
    }

    public OpenApiMetadataBuilder description(final String description) {
        this.description = description;
        return this;
    }

    public OpenApiMetadataBuilder tag(final String tag) {
        this.tags.add(tag);
        return this;
    }

    public OpenApiMetadataBuilder paramDescription(final String param, final String description) {
        this.parameterDescriptions.put(param, description);
        return this;
    }

    public OpenApiMetadataBuilder responseDescription(final String description) {
        this.responseDescription = description;
        return this;
    }

    public OpenApiMetadataBuilder hidden() {
        this.hidden = true;
        return this;
    }

    public RouteConfigBuilder done() {
        return parent;
    }

    public RouteConfig build() {
        return parent.build();
    }

    public OpenApiMetadata buildMetadata() {
        return new OpenApiMetadata(summary, description, tags, parameterDescriptions,
                responseDescription, hidden);
    }
}
