package com.dburyak.exercise.jsonrpc;

import com.dburyak.exercise.jsonrpc.TrackedCall.Change;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.rxjava3.redis.client.RedisConnection;
import lombok.RequiredArgsConstructor;

import java.util.Collection;

@RequiredArgsConstructor
public class TrackedCallRepositoryRedisImpl implements TrackedCallRepository {
    private final RedisConnection redis;

    @Override
    public Completable increment(Collection<Change> calls) {
        return null;
    }

    @Override
    public Maybe<TrackedCall> findByIpAndMethod(String ip, String method) {
        return null;
    }

    @Override
    public Observable<TrackedCall> findAllByIp(String ip) {
        return null;
    }
}
