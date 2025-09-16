package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;

import java.util.Collection;

public interface TrackedCallRepository {
    Completable increment(Collection<TrackedCall.Change> calls);

    Maybe<TrackedCall> findByIpAndMethod(String ip, String method);

    Observable<TrackedCall> findAllByIp(String ip);
}
