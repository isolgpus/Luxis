package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class PostEchoHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
        return e.complete(ctx -> {
            if (ctx.in().responseHeaderExample != null) {
                ctx.session().addResponseHeader("responseHeaderExample", ctx.in().responseHeaderExample);
            }

            if (ctx.in().responseCookieExample != null) {
                ctx.session().addResponseCookie(new HttpCookie("responseCookieExample", ctx.in().responseCookieExample));
            }
            final HttpCookie requestCookieExample = ctx.session().getRequestCookie("requestCookieExample");
            return HttpResult.success(new EchoResponse(
                    ctx.in().intExample,
                    ctx.in().stringExample,
                    ctx.session().getPathParam("pathExample"),
                    ctx.session().getQueryParam("queryExample"),
                    ctx.session().getRequestHeader("requestHeaderExample"),
                    requestCookieExample != null ? requestCookieExample.value() : null));
        });
    }

}
