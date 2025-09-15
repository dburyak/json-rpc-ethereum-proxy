package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.Config;
import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.RequestOptions;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;

import java.util.List;

/**
 * This is the main handler for the application - it forwards the incoming request to one of the backends and sends the
 * backend response back to the client.
 */
public class ReqForwardingHandler implements ReqHandler {
    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    private static final String HOST_HEADER = "host";
    private static final String CONNECTION_HEADER = "connection";

    private final WebClient webClient;
    private final List<RequestOptions> reqOptsList;

    private int reqNum = 0;

    public ReqForwardingHandler(Config cfg, WebClient webClient) {
        this.webClient = webClient;
        this.reqOptsList = cfg.getProxiedBackendUrls().stream()
                .map(url -> {
                    var reqOpts = new RequestOptions();
                    reqOpts.setAbsoluteURI(url);
                    return reqOpts;
                })
                .toList();
    }

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx pReqCtx) {
        // Even though we've already parsed the incoming request body as Json, no need to re-encode it again. We can
        // just forward the original buffer as-is. This will work as long as we don't have any requirements around
        // modifying the request body.
        return pReqCtx.getIncomingReqCtx().request().rxBody()
                .flatMap(inBodyBuf -> {
                    var reqOpts = nextReqOpts();
                    var pReq = webClient.request(pReqCtx.getIncomingReqCtx().request().method(), reqOpts);
                    pReq = populateHeaders(pReq, pReqCtx, reqOpts);
                    return pReq.rxSendBuffer(inBodyBuf)
                            .map(backendResp -> {
                                pReqCtx.setBackendResp(backendResp);
                                return pReqCtx;
                            });
                })
                .toMaybe();
    }

    private <T> HttpRequest<T> populateHeaders(HttpRequest<T> pReq, ProxiedReqCtx pReqCtx, RequestOptions reqOpts) {
        pReq.putHeaders(pReqCtx.getIncomingReqCtx().request().headers());
        if (pReq.headers().contains(HOST_HEADER)) {
            // replace host header that contains this proxy host with the backend host
            pReq.headers().remove(HOST_HEADER);
            pReq.putHeader(HOST_HEADER, reqOpts.getHost());
        }
        pReq.headers().remove(CONNECTION_HEADER);
        pReq.putHeader(CONNECTION_HEADER, "keep-alive");
        var ipInHeader = pReqCtx.getIncomingReqCtx().request().getHeader(X_FORWARDED_FOR_HEADER);
        if (ipInHeader == null) {
            pReq.putHeader(X_FORWARDED_FOR_HEADER, pReqCtx.getIncomingReqCtx().request().remoteAddress().host());
        }
        return pReq;
    }

    private RequestOptions nextReqOpts() {
        var reqMod = reqNum++ % reqOptsList.size();
        var reqOpts = reqOptsList.get(reqMod);
        if (reqNum >= reqOptsList.size()) { // to prevent overflow
            reqNum = 0;
        }
        return reqOpts;
    }
}
