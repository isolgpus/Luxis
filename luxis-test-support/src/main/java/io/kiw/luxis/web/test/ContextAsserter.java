package io.kiw.luxis.web.test;

public interface ContextAsserter {
    void assertInApplicationContext();

    void assertInWorkerContext();

    void notInTransaction();

    void inTransaction();
}
