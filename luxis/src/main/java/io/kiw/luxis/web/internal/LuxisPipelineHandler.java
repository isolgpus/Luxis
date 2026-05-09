package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.http.ErrorMessageResponse;

public interface LuxisPipelineHandler<SESSION> {
    void handleFailure(SESSION session, MapInstruction<?, ?, ?, ?, ?> instruction, ErrorMessageResponse error);

    void sendFinalResponse(SESSION session, Object result);
}
