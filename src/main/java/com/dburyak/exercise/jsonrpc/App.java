package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.config.ConfigRetriever;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class App {

    public static void main(String[] args) {
        var startupStartedAt = Instant.now();
        log.debug("starting");
        var vertx = Vertx.vertx();
        initRxSchedulers(vertx);
        var cfgRetriever = configRetriever(vertx);
        var verticleIds = new AtomicReference<List<String>>();
        cfgRetriever.rxGetConfig()
                .map(Config::new)
                .flatMap(cfg -> {
                    var webClient = buildWebClient(vertx);
                    return Observable.range(0, cfg.getNumVerticles())
                            .flatMapSingle(i -> {
                                // request handlers may be stateful, so we create a separate instance for each verticle
                                var proxiedReqHandlersChain = buildHandlersChain(cfg, webClient);
                                return vertx.rxDeployVerticle(new JsonRpcProxyVerticle(cfg, proxiedReqHandlersChain));
                            })
                            .toList();
                })
                .subscribe(depIds -> {
                    verticleIds.set(depIds);
                    log.info("app started: numVerticles={}, startupTime={}", depIds::size,
                            () -> Duration.between(startupStartedAt, Instant.now()));
                }, err -> {
                    log.error("failed to start", err);
                    vertx.rxClose().subscribe();
                });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            var shutdownStartedAt = Instant.now();
            log.info("shutting down");
            // First undeploy verticles to let them gracefully close resources and finish handling any in-flight
            // requests. Only after that close the Vertx instance which will automatically close any associated
            // resources.
            Observable.fromIterable(verticleIds.get())
                    .flatMapCompletable(vertx::rxUndeploy)
                    .doOnComplete(() -> log.info("all verticles stopped, closing vertx"))
                    .andThen(vertx.rxClose())
                    .blockingAwait();
            log.info("shutdown complete: shutdownTime={}", () -> Duration.between(shutdownStartedAt, Instant.now()));
        }));
    }


    // in a more complex app we'd moved these factory methods into separate factories

    private static void initRxSchedulers(Vertx vertx) {
        var elScheduler = RxHelper.scheduler(vertx);
        var workerScheduler = RxHelper.blockingScheduler(vertx, false);
        RxJavaPlugins.setComputationSchedulerHandler(ignr -> elScheduler);
        RxJavaPlugins.setIoSchedulerHandler(ignr -> workerScheduler);
        RxJavaPlugins.setNewThreadSchedulerHandler(ignr -> elScheduler);
    }

    private static ConfigRetriever configRetriever(Vertx vertx) {
        return ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setFormat("yaml")
                        .setConfig(new JsonObject()
                                .put("path", "config.yaml"))) // matches to the name of src/main/resources/config.yaml
                .addStore(new ConfigStoreOptions()
                        .setType("env")
                        .setConfig(new JsonObject()
                                .put("keys", new JsonArray(Config.ALL_ENV_VARS))))
        );
    }

    private static List<ReqHandler> buildHandlersChain(Config cfg, WebClient webClient) {
        return List.of(new ReqForwardingHandler(cfg, webClient));
    }

    private static WebClient buildWebClient(Vertx vertx) {
        // we pass the "user-agent" header from the incoming request
        return WebClient.create(vertx, new WebClientOptions().setUserAgentEnabled(false));
    }
}
