package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class JwtFilterProtectedHandler implements JsonHandler<Void, SubjectResponse, MyApplicationState> {


    @Override
    public LuxisPipeline<SubjectResponse> handle(final HttpStream<Void, MyApplicationState> stream) {
        return stream
                .complete(ctx ->
                        HttpResult.success(new SubjectResponse(ctx.session().getJwtClaims().getSubject())));
    }
}
