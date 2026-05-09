package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class ValidationTestHandler implements JsonHandler<ValidationRequest, ValidationResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<ValidationResponse> handle(final HttpStream<ValidationRequest, MyApplicationState> httpStream) {
        return httpStream
                .validateHttp(v -> {
                    v.field("name", r -> r.name).required().minLength(2);
                    v.field("email", r -> r.email).required().email();
                    v.field("age", r -> r.age).required().min(0).max(150);
                    v.field("address", r -> r.address, a -> {
                        a.field("city", x -> x.city).required();
                        a.field("zip", x -> x.zip).required().matches("[0-9]{5}");
                    });
                    v.queryParam("page").required().matches("[0-9]+");
                    v.pathParam("userId").required().matches("[0-9]+");
                })
                .complete(ctx -> {
                    final ValidationRequest r = ctx.in();
                    final String page = ctx.session().getQueryParam("page");
                    final String userId = ctx.session().getPathParam("userId");
                    return success(new ValidationResponse(r.name, r.email, r.age, r.address.city, page, userId));
                });
    }
}
