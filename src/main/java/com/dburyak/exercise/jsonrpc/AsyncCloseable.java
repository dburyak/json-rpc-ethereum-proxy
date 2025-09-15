package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;

public interface AsyncCloseable {
    default Completable closeAsync() {
        return Completable.complete();
    }
}
