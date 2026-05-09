package io.kiw.luxis.web.handler;

import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.websocket.WebSocketSession;

public abstract class WebSocketRoutes<APP, RESP> {

    public void onOpen(final WebSocketSession<RESP> session, final APP appState) {
    }

    public abstract void registerRoutes(final WebSocketRoutesRegister<APP, RESP> splitStream);

    public void onClose(final WebSocketSession<RESP> session, final APP appState) {
    }
}
