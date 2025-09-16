package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

import java.util.Collection;
import java.util.List;

public interface TrackedCallRepository {
    Completable increment(Collection<TrackedCall.Change> calls);

    Maybe<TrackedCall> findByIpAndMethod(String ip, String method);

    Maybe<List<TrackedCall>> findAllByIp(String ip);
}
