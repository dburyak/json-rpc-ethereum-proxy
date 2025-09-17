package com.dburyak.exercise.jsonrpc.repo;

import com.dburyak.exercise.jsonrpc.entity.CallsOfUser;
import com.dburyak.exercise.jsonrpc.entity.TrackedCall;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Collection;

public interface TrackedCallRepository {
    Completable increment(Collection<TrackedCall.Change> calls);

    Maybe<TrackedCall> findByIpAndMethod(String ip, String method);

    Maybe<CallsOfUser> findByIp(String ip);

    Single<Boolean> deleteByIp(String ip);
}
