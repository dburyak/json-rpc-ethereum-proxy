package com.dburyak.exercise.jsonrpc;

import com.dburyak.exercise.jsonrpc.err.ProxyPublicException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.http.HttpServer;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@RequiredArgsConstructor
@Log4j2
public class JsonRpcProxyVerticle extends AbstractVerticle {
    private final Config cfg;
    private final List<ReqHandler> handlers;

    private HttpServer httpServer;

    @Override
    public Completable rxStart() {
        return Single.fromSupplier(this::buildRouter)
                .flatMap(router -> {
                    httpServer = vertx.createHttpServer();
                    return httpServer
                            .requestHandler(router)
                            .rxListen(cfg.getPort())
                            .doOnSuccess(srv ->
                                    log.info("verticle http server started: verticleId={}, port={}",
                                            deploymentID(), srv.actualPort()));
                })
                .ignoreElement();
    }

    @Override
    public Completable rxStop() {
        return httpServer.rxShutdown(cfg.getGracefulShutdownTimeout().toMillis(), MILLISECONDS)
                .andThen(Observable.fromIterable(handlers))
                .flatMapCompletable(AsyncCloseable::closeAsync)
                .doOnComplete(() -> log.info("verticle stopped: verticleId={}", deploymentID()));
    }

    private Router buildRouter() {
        var router = Router.router(vertx);
        router.route(cfg.getApiPath()).handler(BodyHandler.create());
        router.route(cfg.getApiPath()).handler(reqCtx -> {
            var proxiedReqCtx = new ProxiedReqCtx(reqCtx);
            processWithTheChain(proxiedReqCtx)
                    // if the Maybe is empty, it means that one of the handlers has already responded
                    .flatMapCompletable(pCtx -> {
                        reqCtx.response().headers().addAll(pCtx.getBackendResp().headers());
                        return reqCtx.response()
                                .setStatusCode(pCtx.getBackendResp().statusCode())
                                .setStatusMessage(pCtx.getBackendResp().statusMessage())
                                .rxEnd(pCtx.getBackendResp().body());
                    })
                    .subscribe(() -> {
                    }, err -> {
                        if (err instanceof ProxyPublicException publicErr) {
                            log.debug("request processing failed", publicErr);
                            if (!reqCtx.response().ended()) {
                                reqCtx.response()
                                        .setStatusCode(publicErr.getHttpStatusCode())
                                        .rxEnd(JsonRpcResponse.failed(publicErr).toJson().toBuffer())
                                        .subscribe(() -> {}, err2 ->
                                                log.error("failed to respond with {}",
                                                        publicErr.getHttpStatusCode(), err2));
                            }
                        } else {
                            log.error("unexpected error in the proxy itself, responding with 500", err);
                            if (!reqCtx.response().ended()) {
                                reqCtx.response().setStatusCode(500).rxEnd().subscribe(() -> {},
                                        err2 -> log.error("failed to respond with 500", err2));
                            }
                        }
                    });
        });
        return router;
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
