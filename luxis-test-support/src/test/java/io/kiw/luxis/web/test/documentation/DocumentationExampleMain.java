package io.kiw.luxis.web.test.documentation;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.http.Method;

public final class DocumentationExampleMain {
    private DocumentationExampleMain() {
    }

    public static void main(final String[] args) {

        Luxis.app(
                routesRegister -> {
                    final AppState appState = new AppState();

                    routesRegister.jsonRoute("/hello/world", Method.POST, appState, HelloWorldRequest.class, new HelloWorldHandler());
                    return appState;
                }).start();

    }
}
