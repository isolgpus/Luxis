package io.kiw.luxis.web.http;

public enum Method {
    POST(true),
    PUT(true),
    GET(false),
    DELETE(true),
    PATCH(true),
    OPTIONS(false);

    private final boolean requiresBody;

    Method(final boolean requiresBody) {
        this.requiresBody = requiresBody;
    }

    public boolean canHaveABody() {
        return requiresBody;
    }
}
