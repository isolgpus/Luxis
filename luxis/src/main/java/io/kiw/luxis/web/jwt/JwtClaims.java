package io.kiw.luxis.web.jwt;

import java.util.Collections;
import java.util.Map;

public record JwtClaims(Map<String, Object> claims) {

    private static final String SUBJECT_CLAIM = "sub";

    public Object getClaim(final String key) {
        return claims.get(key);
    }

    public String getSubject() {
        final Object sub = claims.get(SUBJECT_CLAIM);
        return sub != null ? sub.toString() : null;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(claims);
    }
}
