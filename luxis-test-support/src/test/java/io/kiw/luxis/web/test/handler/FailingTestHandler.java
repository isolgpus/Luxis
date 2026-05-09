package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.result.Result.success;


public class FailingTestHandler implements JsonHandler<BlockingRequest, BlockingTestResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<BlockingTestResponse> handle(final HttpStream<BlockingRequest, MyApplicationState> httpStream) {
        return
                httpStream
                        .map(ctx -> ctx.in().numberToMultiply)
                        .flatMap(ctx -> {
                            if (2 * 2 == 4) {
                                return HttpResult.error(ErrorStatusCode.BAD_REQUEST, new ErrorMessageResponse("intentionally failed"));
                            } else {
                                return success(4);
                            }
                        })
                        // should never reach this
                        .complete(ctx -> success(new BlockingTestResponse(ctx.in())));
    }
}
