package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import com.github.benmanes.caffeine.cache.Cache;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.redis.client.RedisConnection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GlobalIpRateLimiter implements ReqHandler {
    private static final String DELIMITER = ":";
    private static final String PREFIX = "rtlmt" + DELIMITER;
    private final Cache<String, Integer> localCache;
    private final RedisConnection redis;

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
    }

    @Override
    public Completable closeAsync() {
        // TODO: track in-flight redis requests and wait for them to complete
        return ReqHandler.super.closeAsync();
    }
}
