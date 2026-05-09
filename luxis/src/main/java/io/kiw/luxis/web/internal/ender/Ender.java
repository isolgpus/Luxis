package io.kiw.luxis.web.internal.ender;

import io.kiw.luxis.web.http.RequestContext;

public sealed interface Ender permits FileEnder, JsonEnder {

    void end(final RequestContext requestContext, final Object value);
}
