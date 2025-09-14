package com.dburyak.exercise.jsonrpc;

import io.reactivex.rxjava3.core.Observable;
import io.vertx.rxjava3.config.ConfigRetriever;
import io.vertx.rxjava3.core.Vertx;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.Instant;

@Log4j2
public class App {

    public static void main(String[] args) {
        var startupStartedAt = Instant.now();
        log.debug("starting...");
        var vertx = Vertx.vertx();
        var cfgRetriever = ConfigRetriever.create(vertx);
        cfgRetriever.rxGetConfig()
                .map(Config::new)
                .flatMap(cfg -> Observable.range(0, cfg.getNumVerticles())
                        .flatMapSingle(i -> vertx.rxDeployVerticle(new JsonRpcProxyVerticle(cfg)))
                        .toList())
                .subscribe((depIds) -> {
                    log.info("proxy started: numVerticles={}, startTime={}", depIds.size(),
                            Duration.between(startupStartedAt, Instant.now()));
                }, err -> {
                    log.error("failed to start", err);
                    vertx.rxClose().subscribe();
                });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            var shutdownStartedAt = Instant.now();
            log.info("shutting down...");
            vertx.rxClose().blockingAwait();
            log.info("shutdown complete: shutdownTime={}", Duration.between(shutdownStartedAt, Instant.now()));
        }));
    }
}
