package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class ValidationWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("validationResponse", WebSocketValidationResponse.class);

        routesRegister
                .registerInbound("validate", ValidationRequest.class, s ->
                        s.validate(v -> {
                                    v.field("name", r -> r.name).required().minLength(2);
                                    v.field("email", r -> r.email).required().email();
                                    v.field("age", r -> r.age).required().min(0).max(150);
                                    v.field("address", r -> r.address, a -> {
                                        a.field("city", x -> x.city).required();
                                        a.field("zip", x -> x.zip).required().matches("[0-9]{5}");
                                    });
                                })
                                .map(ctx -> {
                                    final ValidationRequest r = ctx.in();
                                    return new WebSocketValidationResponse(r.name, r.email, r.age, r.address.city);
                                })
                                .complete());

    }
}
