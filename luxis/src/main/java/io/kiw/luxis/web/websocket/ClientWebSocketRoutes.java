package io.kiw.luxis.web.websocket;

import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;

public abstract class ClientWebSocketRoutes<APP, RESP> {


    public abstract void registerRoutes(final WebSocketRoutesRegister<APP, RESP> routesRegister);

}
