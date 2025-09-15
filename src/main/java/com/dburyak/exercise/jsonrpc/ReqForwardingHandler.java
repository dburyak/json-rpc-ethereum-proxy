package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReqForwardingHandler implements ReqHandler {
    private final Config cfg;
    private final WebClient webClient;

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx pReqCtx) {
        // Even though we've already parsed the incoming request body as Json, no need to re-encode it again. We can
        // just forward the original buffer as-is. This will work as long as we don't have any requirements around
        // modifying the request body.
        return pReqCtx.getIncomingReqCtx().request().rxBody()
                .flatMap(inBodyBuf -> {
                    // FIXME: put URL from cfg
                    var pReq = webClient.request(pReqCtx.getIncomingReqCtx().request().method(), "change-me")
                            .putHeaders(pReqCtx.getIncomingReqCtx().request().headers());
                    pReq = withEnrichedHeaders(pReq, pReqCtx);
                    return pReq.rxSendBuffer(inBodyBuf)
                            .map(backendResp -> {
                                pReqCtx.setBackendResp(backendResp);
                                return pReqCtx;
                            });
                })
                .toMaybe();
    }

    private <T> HttpRequest<T> withEnrichedHeaders(HttpRequest<T> pReq, ProxiedReqCtx pReqCtx) {
        // FIXME: check with debugger what's there and calculate the correct value
        var callerAddr = pReqCtx.getIncomingReqCtx().request().remoteAddress();
        pReq.headers().add("X-Forwarded-For", "change-me");
        return pReq;
    }
}
