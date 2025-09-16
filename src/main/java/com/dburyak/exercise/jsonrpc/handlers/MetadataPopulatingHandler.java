package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Handler that populates metadata in the ProxiedReqCtx.
 */
public class MetadataPopulatingHandler implements ReqHandler {
    public static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
        return Maybe.fromSupplier(() -> {
            var ipInHeader = reqCtx.getIncomingReqCtx().request().getHeader(X_FORWARDED_FOR_HEADER);
            if (ipInHeader != null) {
                reqCtx.setCallersIp(ipInHeader);
            } else {
                reqCtx.setCallersIp(reqCtx.getIncomingReqCtx().request().remoteAddress().host());
            }
            return reqCtx;
        });
    }
}
