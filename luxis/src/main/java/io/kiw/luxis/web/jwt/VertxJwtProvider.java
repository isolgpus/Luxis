package io.kiw.luxis.web.jwt;

import io.kiw.luxis.result.Result;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VertxJwtProvider implements JwtProvider {

    private final JWTAuth jwtAuth;

    public VertxJwtProvider(final Vertx vertx, final String secret) {
        this(vertx, secret, "HS256");
    }

    public VertxJwtProvider(final Vertx vertx, final String secret, final String algorithm) {
        final JWTAuthOptions config = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm(algorithm)
                        .setBuffer(secret));
        this.jwtAuth = JWTAuth.create(vertx, config);
    }

    @Override
    public String generateToken(final Map<String, Object> claims) {
        return jwtAuth.generateToken(new JsonObject(claims), new JWTOptions());
    }

    @Override
    public Result<String, JwtClaims> validateToken(final String token) {
        try {
            final Future<User> future = jwtAuth.authenticate(new TokenCredentials(token));
            final CompletableFuture<User> cf = future.toCompletionStage().toCompletableFuture();
            final User user = cf.get(5, TimeUnit.SECONDS);
            return Result.success(new JwtClaims(user.principal().getMap()));
        } catch (final ExecutionException e) {
            return Result.error("Invalid token");
        } catch (final InterruptedException | TimeoutException e) {
            Thread.currentThread().interrupt();
            return Result.error("Token validation failed");
        }
    }
}
