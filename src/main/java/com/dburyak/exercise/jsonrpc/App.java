package com.dburyak.exercise.jsonrpc;

import com.dburyak.exercise.jsonrpc.handlers.GlobalIpRateLimiter;
import com.dburyak.exercise.jsonrpc.handlers.JsonRpcParsingHandler;
import com.dburyak.exercise.jsonrpc.handlers.MetadataPopulatingHandler;
import com.dburyak.exercise.jsonrpc.handlers.PerMethodRateLimiter;
import com.dburyak.exercise.jsonrpc.handlers.ReqForwardingHandler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.rxjava3.core.Completable;
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
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.redis.client.Redis;
import io.vertx.rxjava3.redis.client.RedisConnection;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Log4j2
public class App {
    private volatile Vertx vertx;
    private volatile Config cfg;
    private volatile List<String> verticleIds = List.of();
    private volatile HttpClient httpClient;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public static void main(String[] args) {
        new App().start();
    }

    public void start() {
        var startupStartedAt = Instant.now();
        log.debug("starting");
        vertx = Vertx.vertx();
        initRxSchedulers(vertx);
        var cfgRetriever = configRetriever(vertx);
        cfgRetriever.rxGetConfig()
                .map(Config::new)
                .flatMap(cfg -> {
                    this.cfg = cfg;
                    httpClient = buildHttpClient(vertx);
                    var webClient = buildWebClient(httpClient);
                    var redisClient = buildRedisClient(vertx, cfg);
                    var globalIpRtlmtCache = buildGlobalIpRtlmtCaffeineCache(cfg);
                    var perMethodIpRtlmtCache = buildPerMethodIpRtlmtCaffeineCache(cfg);
                    return redisClient.rxConnect().flatMap(redis ->
                            Observable.range(0, cfg.getNumVerticles())
                                    .flatMapSingle(i -> {
                                        // request handlers may be stateful, so we create a separate instance for each
                                        // verticle
                                        var proxiedReqHandlersChain = buildHandlersChain(cfg, webClient,
                                                redis, globalIpRtlmtCache, perMethodIpRtlmtCache);
                                        return vertx.rxDeployVerticle(new JsonRpcProxyVerticle(cfg,
                                                proxiedReqHandlersChain));
                                    })
                                    .toList());
                })
                .subscribe(depIds -> {
                    verticleIds = depIds;
                    log.info("app started: numVerticles={}, startupTime={}", depIds::size,
                            () -> Duration.between(startupStartedAt, Instant.now()));
                }, err -> {
                    log.error("failed to start", err);
                    vertx.rxClose().subscribe();
                });
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void shutdown() {
        // shutdown potentially may be called multiple times from tests
        if (!isShuttingDown.compareAndSet(false, true)) {
            log.warn("multiple shutdown calls, shutdown already in progress, ignoring");
            return;
        }
        var shutdownStartedAt = Instant.now();
        log.info("shutting down");
        // Graceful shutdown in 3 steps:
        // - first undeploy verticles to let them gracefully close their resources and stop receiving new requests (at
        //   this stage we still can have some queued up downstream requests)
        // - then gracefully shutdown (not the same as "close") the HttpClient we use to call proxied backends
        // - now we're good to close the Vertx instance. Vertx automatically closes any associated resources created
        //   via its API (e.g. HttpClient, Redis client, etc.), but does that abruptly killing any in-flight data.
        //   That's why we need first two steps before closing Vertx.
        // NOTE: there's graceful shutdown timeout inaccuracy as we need to apply timeout to ALL the steps together.
        // It's not worth to implement more complex logic for this exercise.
        var closeHttpClient = (httpClient != null)
                ? httpClient.rxShutdown(cfg.getGracefulShutdownTimeout().toMillis(), MILLISECONDS)
                : Completable.complete();
        Observable.fromIterable(verticleIds)
                .flatMapCompletable(vertx::rxUndeploy)
                .doOnComplete(() -> log.info("all verticles stopped, closing downstream http client"))
                .andThen(closeHttpClient)
                .doOnComplete(() -> log.info("closing vertx"))
                .andThen(vertx.rxClose())
                .blockingAwait();
        log.info("shutdown complete: shutdownTime={}", () -> Duration.between(shutdownStartedAt, Instant.now()));
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

    private static List<ReqHandler> buildHandlersChain(Config cfg, WebClient webClient, RedisConnection redis,
            Cache<String, Long> globalIpRtlmtCache, Cache<String, Long> perMethodIpRtlmtCache) {
        var handlers = new ArrayList<ReqHandler>();
        handlers.add(new MetadataPopulatingHandler()); // 1 - populate metadata (e.g. caller's IP)
        if (cfg.getGlobalIpRateLimiting().isEnabled()) {
            handlers.add(new GlobalIpRateLimiter(cfg, globalIpRtlmtCache, redis)); // 2 - global IP rate limiter
        }
        handlers.add(new JsonRpcParsingHandler()); // 3 - parse and validate JSON-RPC request
        if (cfg.getPerMethodIpRateLimiting().isEnabled()) {
            handlers.add(new PerMethodRateLimiter(cfg, perMethodIpRtlmtCache, redis)); // 4 - per-method IP rate limiter
        }
        handlers.add(new ReqForwardingHandler(cfg, webClient)); // 5 - forward the request to backend and respond
        return handlers;
    }

    private static HttpClient buildHttpClient(Vertx vertx) {
        return vertx.createHttpClient();
    }

    private static WebClient buildWebClient(HttpClient httpClient) {
        // we pass the "user-agent" header value down from the incoming request
        return WebClient.wrap(httpClient, new WebClientOptions().setUserAgentEnabled(false));
    }

    private static Redis buildRedisClient(Vertx vertx, Config cfg) {
        return Redis.createClient(vertx);
    }

    private static Cache<String, Long> buildGlobalIpRtlmtCaffeineCache(Config cfg) {
        if (!cfg.getGlobalIpRateLimiting().isEnabled()) {
            return null;
        }
        return Caffeine.newBuilder()
                .maximumSize(cfg.getGlobalIpRateLimiting().getLocalCacheSize())
                .build();
    }

    private static Cache<String, Long> buildPerMethodIpRtlmtCaffeineCache(Config cfg) {
        if (!cfg.getPerMethodIpRateLimiting().isEnabled()) {
            return null;
        }
        return Caffeine.newBuilder()
                .maximumSize(cfg.getPerMethodIpRateLimiting().getLocalCacheSize())
                .build();
    }
}
