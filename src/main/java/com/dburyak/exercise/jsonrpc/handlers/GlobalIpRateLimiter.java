package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.Config;
import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import com.github.benmanes.caffeine.cache.Cache;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.rxjava3.redis.client.RedisConnection;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.vertx.redis.client.Command.INCR;
import static io.vertx.redis.client.Command.PEXPIRE;
import static io.vertx.redis.client.Command.PTTL;
import static io.vertx.redis.client.Request.cmd;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Log4j2
public class GlobalIpRateLimiter implements ReqHandler {
    private static final String DELIMITER = ":";
    private static final String PREFIX = "rtlmt" + DELIMITER;
    private final Cache<String, Long> localCache;
    private final RedisConnection redis;
    private final int maxCallsInWindow;
    private final long windowMs;
    private final Duration gracefulShutdownTimeout;

    // Vertx event-loop is single-threaded, and we create separate handler instance for each verticle, so we don't
    // need any concurrency control here
    private int inFlightRequests = 0;

    public GlobalIpRateLimiter(Config cfg, Cache<String, Long> localCache, RedisConnection redis) {
        this.localCache = localCache;
        this.redis = redis;
        this.maxCallsInWindow = cfg.getGlobalIpRateLimiting().getRequests();
        this.windowMs = cfg.getGlobalIpRateLimiting().getTimeWindow().toMillis();
        this.gracefulShutdownTimeout = cfg.getGracefulShutdownTimeout();
    }

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
        return Maybe.defer(() -> {
            var ip = reqCtx.getCallersIp();
            var nowMs = System.currentTimeMillis();
            if (cachedHitLimit(ip, nowMs)) {
                return respondWithTooManyRequests(reqCtx).andThen(Maybe.empty());
            }
            var redisKey = redisKey(ip);
            var incrReq = cmd(INCR).arg(redisKey);
            var pttlReq = cmd(PTTL).arg(redisKey);
            return redis.rxBatch(List.of(incrReq, pttlReq))
                    .doOnSubscribe(ignr -> inFlightRequests += 2)
                    .doFinally(() -> inFlightRequests -= 2)
                    .flatMapMaybe(responses -> {
                        var cnt = responses.get(0).toInteger();
                        var ttlMs = responses.get(1).toLong();
                        var hitTheLimit = cnt > maxCallsInWindow;
                        if (hitTheLimit && ttlMs > 0) {
                            localCache.put(ip, nowMs + ttlMs);
                        }
                        if (cnt == 1) {
                            // set TTL on the first hit within the window
                            // NOTE: in general cases, to make multiple operations atomic, we would use a lua script,
                            // but in this particular case it's acceptable because only OOM (or any other similar
                            // unexpected failure) that happen after "INCR" but before the "PEXPIRE" may lead to a
                            // non-expirable key, and it's fine for the exercise purposes
                            var expireReq = cmd(PEXPIRE).arg(redisKey).arg(windowMs);
                            return redis.rxSend(expireReq)
                                    .doOnSubscribe(ignr -> inFlightRequests += 1)
                                    .doFinally(() -> inFlightRequests -= 1)
                                    .map(ignr -> reqCtx);
                        } else if (hitTheLimit) {
                            return respondWithTooManyRequests(reqCtx).andThen(Maybe.empty());
                        } else {
                            return Maybe.just(reqCtx);
                        }
                    });
        });
    }

    @Override
    public Completable closeAsync() {
        log.debug("closing, inFlightRequests={}", inFlightRequests);
        if (inFlightRequests <= 0) {
            return Completable.complete();
        }
        // there's a way to implement it with listeners/Promises without polling, but it's more complex and requires
        // more memory and CPU wasted on each request, so polling being ugly still is not a bad trade-off here
        return Observable.interval(0, 50, MILLISECONDS)
                .filter(ignr -> inFlightRequests <= 0)
                .take(1)
                .ignoreElements()
                .timeout(gracefulShutdownTimeout.toMillis(), MILLISECONDS);
    }

    private boolean cachedHitLimit(String ip, long nowMs) {
        var cachedWindowExpiresAt = localCache.getIfPresent(ip);
        if (cachedWindowExpiresAt != null) {
            if (cachedWindowExpiresAt < nowMs) {
                // time-window expired, remove from cache and let the request through
                localCache.invalidate(ip);
            } else {
                // already hit the limit in the current time-window
                return true;
            }
        }
        return false;
    }

    private Completable respondWithTooManyRequests(ProxiedReqCtx reqCtx) {
        return reqCtx.getIncomingReqCtx().response()
                .setStatusCode(TOO_MANY_REQUESTS.code())
                .setStatusMessage(TOO_MANY_REQUESTS.reasonPhrase())
                .rxEnd();
    }

    private String redisKey(String ip) {
        return PREFIX + ip;
    }
}
