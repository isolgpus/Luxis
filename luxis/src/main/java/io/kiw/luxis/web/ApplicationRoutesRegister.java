package io.kiw.luxis.web;

import io.kiw.luxis.web.internal.RoutesRegister;

public interface ApplicationRoutesRegister<R> {
    R registerRoutes(final RoutesRegister routesRegister);
}
