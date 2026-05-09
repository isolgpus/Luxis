package io.kiw.luxis.web.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OpenApiCollector {
    private final List<RouteDescriptor> routes = new ArrayList<>();

    public void addRoute(final RouteDescriptor descriptor) {
        routes.add(descriptor);
    }

    public List<RouteDescriptor> getRoutes() {
        return Collections.unmodifiableList(routes);
    }
}
