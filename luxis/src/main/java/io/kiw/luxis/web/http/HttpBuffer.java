package io.kiw.luxis.web.http;

import java.nio.charset.StandardCharsets;

public record HttpBuffer(byte[] bytes) {
    public static HttpBuffer fromString(final String s) {
        return new HttpBuffer(s.getBytes(StandardCharsets.UTF_8));
    }

    public String toString(final java.nio.charset.Charset charset) {
        return new String(bytes, charset);
    }
}
