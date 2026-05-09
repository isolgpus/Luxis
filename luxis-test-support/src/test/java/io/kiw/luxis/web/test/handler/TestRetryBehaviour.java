package io.kiw.luxis.web.test.handler;

import java.util.ArrayList;
import java.util.List;

public final class TestRetryBehaviour {

    public enum AttemptAction {
        SUCCESS,
        ERROR,
        EXCEPTION,
        TIMEOUT
    }

    private final List<AttemptAction> attempts = new ArrayList<>();

    public TestRetryBehaviour success() {
        attempts.add(AttemptAction.SUCCESS);
        return this;
    }

    public TestRetryBehaviour error() {
        attempts.add(AttemptAction.ERROR);
        return this;
    }

    public TestRetryBehaviour exception() {
        attempts.add(AttemptAction.EXCEPTION);
        return this;
    }

    public TestRetryBehaviour timeout() {
        attempts.add(AttemptAction.TIMEOUT);
        return this;
    }

    public AttemptAction getAction(int attempt) {
        return attempts.get(attempt);
    }
}
