package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Maybe;

public interface ReqHandler extends AsyncCloseable {
    Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx);
}
