package io.kiw.luxis.web.test.jwt;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.internal.JacksonUtil;
import io.kiw.luxis.web.jwt.JwtClaims;
import io.kiw.luxis.web.jwt.JwtProvider;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class StubJwtProvider implements JwtProvider {

    private static final ObjectMapper MAPPER = JacksonUtil.createMapper();
    private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String secret;

    public StubJwtProvider(final String secret) {
        this.secret = secret;
    }

    @Override
    public String generateToken(final Map<String, Object> claims) {
        try {
            final String header = base64UrlEncode(JWT_HEADER);
            final String payload = base64UrlEncode(MAPPER.writeValueAsString(claims));
            final String signingInput = header + "." + payload;
            return signingInput + "." + sign(signingInput);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }

    @Override
    public Result<String, JwtClaims> validateToken(final String token) {
        try {
            final String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Result.error("Invalid token format");
            }

            final String signingInput = parts[0] + "." + parts[1];
            if (!sign(signingInput).equals(parts[2])) {
                return Result.error("Invalid token signature");
            }

            final String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            final Map<String, Object> claims = MAPPER.readValue(payloadJson, new TypeReference<>() {
            });

            final Object exp = claims.get("exp");
            if (exp != null && System.currentTimeMillis() / 1000 > ((Number) exp).longValue()) {
                return Result.error("Token has expired");
            }

            return Result.success(new JwtClaims(claims));
        } catch (final Exception e) {
            return Result.error("Token validation failed");
        }
    }

    private String sign(final String input) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }

    private String base64UrlEncode(final String input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
