package io.kiw.luxis.web.openapi;

import io.kiw.luxis.web.http.Method;

import java.lang.reflect.Type;

public record RouteDescriptor(String path, Method method, Type inputType, Type outputType,
                              String consumes, String produces, RouteKind kind,
                              OpenApiMetadata metadata) {

    public enum RouteKind {
        JSON, UPLOAD, DOWNLOAD, WEBSOCKET, FILTER
    }
}
