package io.kiw.luxis.web.test;

import io.kiw.luxis.web.internal.TransactionStatus;

public class StubContextAsserter implements ContextAsserter {
    @Override
    public void assertInApplicationContext() {
        final String threadName = Thread.currentThread().getName();
        if (!threadName.contains("Application")) {
            throw new AssertionError("Expected to be on application context but was on: " + threadName);
        }
    }

    @Override
    public void assertInWorkerContext() {
        final String threadName = Thread.currentThread().getName();
        if (!threadName.contains("Worker")) {
            throw new AssertionError("Expected to be on Worker context but was on: " + threadName);
        }
    }

    @Override
    public void notInTransaction() {
        if (TransactionStatus.isInTransaction()) {
            throw new AssertionError("Expected NOT to be in a transaction, but was");
        }
    }

    @Override
    public void inTransaction() {
        if (!TransactionStatus.isInTransaction()) {
            throw new AssertionError("Expected to be in a transaction, but was not");
        }
    }
}
