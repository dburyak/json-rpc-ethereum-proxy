package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.AbstractVerticle;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JsonRpcProxyVerticle extends AbstractVerticle {
    private final Config cfg;

    @Override
    public Completable rxStart() {
        return super.rxStart();
    }

    @Override
    public Completable rxStop() {
        return super.rxStop();
    }
}
