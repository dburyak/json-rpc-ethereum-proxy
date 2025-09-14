package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.Router;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

@RequiredArgsConstructor
@Log4j2
public class JsonRpcProxyVerticle extends AbstractVerticle {
    private final Config cfg;

    @Override
    public Completable rxStart() {
        return Single
                .fromSupplier(() -> {
                    var router = Router.router(vertx);
                    router.route(cfg.getApiPath()).handler(reqCtx -> {
                        log.debug("req: ");
                        reqCtx.rxJson(new JsonObject(Map.of("resp", "test"))).subscribe();
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
}
