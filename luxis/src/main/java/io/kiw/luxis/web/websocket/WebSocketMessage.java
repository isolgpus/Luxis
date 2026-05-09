package io.kiw.luxis.web.websocket;

import tools.jackson.databind.JsonNode;

public record WebSocketMessage(String type, JsonNode payload) {
}
