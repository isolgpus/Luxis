package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class GetEchoHandler implements JsonHandler<Void, EchoResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<EchoResponse> handle(final HttpStream<Void, MyApplicationState> httpStream) {
        return
                httpStream
                        .complete(ctx -> {
                            final HttpCookie requestCookieExample = ctx.session().getRequestCookie("requestCookieExample");

                            return HttpResult.success(new EchoResponse(188,
                                    "You invoked a GET",
                                    ctx.session().getPathParam("pathExample"),
                                    ctx.session().getQueryParam("queryExample"),
                                    ctx.session().getRequestHeader("requestHeaderExample"),
                                    requestCookieExample != null ? requestCookieExample.value() : null));
                        });
    }
}
