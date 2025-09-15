package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.Router;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@RequiredArgsConstructor
@Log4j2
public class JsonRpcProxyVerticle extends AbstractVerticle {
    private final Config cfg;
    private final List<ReqHandler> handlers;

    @Override
    public Completable rxStart() {
        return Single
                .fromSupplier(() -> {
                    var router = Router.router(vertx);
                    router.route(cfg.getApiPath()).handler(reqCtx -> {
                        var proxiedReqCtx = new ProxiedReqCtx(reqCtx);
                        processWithTheChain(proxiedReqCtx)
                                // if the Maybe is empty, it means that one of the handlers has already responded
                                .flatMapCompletable(pCtx -> {
                                    reqCtx.response().headers().addAll(pCtx.getBackendResp().headers());
                                    return reqCtx.response().rxEnd(pCtx.getBackendResp().body());
                                })
                                .subscribe();
                    });
                    return router;
                })
                .flatMap(router -> vertx.createHttpServer()
                        .requestHandler(router)
                        .rxListen(cfg.getPort())
                        .doOnSuccess(srv ->
                                log.info("verticle http server started: verticleId={}, port={}",
                                        deploymentID(), srv.actualPort())))
                .ignoreElement();
    }

    @Override
    public Completable rxStop() {
        return Completable.fromRunnable(() -> {
            log.info("verticle stopped: verticleId={}", deploymentID());
        });
    }

    private Maybe<ProxiedReqCtx> processWithTheChain(ProxiedReqCtx reqCtx) {
        // handlers chain always contains at least one handler that forwards the request to the backend
        var iter = handlers.iterator();
        var first = iter.next();
        var result = first.handle(reqCtx);
        while (iter.hasNext()) {
            var next = iter.next();
            result = result.flatMap(next::handle);
        }
        return result;
    }
}
