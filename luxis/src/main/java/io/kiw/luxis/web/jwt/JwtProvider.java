package io.kiw.luxis.web.jwt;

import io.kiw.luxis.result.Result;

import java.util.Map;

public interface JwtProvider {

    String generateToken(Map<String, Object> claims);

    Result<String, JwtClaims> validateToken(String token);
}
