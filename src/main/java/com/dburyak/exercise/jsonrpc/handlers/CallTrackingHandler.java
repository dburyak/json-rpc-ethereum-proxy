package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.Config;
import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import com.dburyak.exercise.jsonrpc.entity.TrackedCall;
import com.dburyak.exercise.jsonrpc.repo.TrackedCallRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.Subject;
import io.reactivex.rxjava3.subjects.UnicastSubject;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.groupingBy;

@Log4j2
public class CallTrackingHandler implements ReqHandler {
    private static final Duration BATCH_SAVE_INTERVAL = Duration.ofSeconds(1); // this could be configurable
    private final TrackedCallRepository repo;
    private final Duration gracefulShutdownTimeout;
    private final Subject<Call> calls = UnicastSubject.create();

    // Vertx event-loop is single-threaded, and we create separate handler instance for each verticle, so we don't
    // need any concurrency control here
    private int inFlightRequests = 0;
    private Disposable persistenceSubscription;

    public CallTrackingHandler(Config cfg, TrackedCallRepository repo) {
        this.repo = repo;
        this.gracefulShutdownTimeout = cfg.getGracefulShutdownTimeout();
    }

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
        return Maybe.fromSupplier(() -> {
            if (persistenceSubscription == null) {
                // We can't start it from the constructor as it's called on a thread different from the EL of the
                // verticle (because handlers are created before deploying verticles in current design). Better
                // long-term solution would be to introduce AsyncStartable with "startAsync" (similar to AsyncCloseable)
                // and let each verticle to start its components during verticle startup each on its EL thread.
                startCallsPersistenceHandler();
            }
            var isSuccessful = reqCtx.getBackendResp().statusCode() == OK.code();
            var call = new Call(reqCtx.getCallersIp(), reqCtx.getJsonRpcRequest().getMethod(), isSuccessful);
            calls.onNext(call);
            inFlightRequests++;
            return reqCtx;
        });
    }

    @Override
    public Completable closeAsync() {
        log.debug("closing, inFlightRequests={}", inFlightRequests);
        if (inFlightRequests <= 0) {
            return Completable.complete();
        }
        // there's a way to implement it with listeners/Promises without polling, but it's more complex and requires
        // more memory and CPU wasted on each request, so polling being ugly still is not a bad trade-off here
        return Observable.interval(0, 50, MILLISECONDS)
                .filter(ignr -> inFlightRequests <= 0)
                .take(1)
                .ignoreElements()
                .timeout(gracefulShutdownTimeout.toMillis(), MILLISECONDS, Completable.complete());
    }

    @Value
    private static class Call {
        String ip;
        String method;
        boolean successful;
    }

    private void startCallsPersistenceHandler() {
        log.debug("starting calls persistence handler");
        persistenceSubscription = calls.buffer(BATCH_SAVE_INTERVAL.toMillis(), MILLISECONDS)
                .filter(c -> !c.isEmpty())
                .flatMapSingle(callsBatch -> {
                    var byIpAndMethod = callsBatch.stream()
                            .collect(groupingBy(Call::getIp, groupingBy(Call::getMethod)));
                    var callChanges = byIpAndMethod.entrySet().stream().flatMap(eIp -> {
                        var ip = eIp.getKey();
                        return eIp.getValue().entrySet().stream().map(eMtd -> {
                            var method = eMtd.getKey();
                            var successfulCnt = eMtd.getValue().stream().filter(Call::isSuccessful).count();
                            var failedCnt = eMtd.getValue().size() - successfulCnt;
                            return new TrackedCall.Change(ip, method, successfulCnt, failedCnt);
                        });
                    }).toList();
                    return repo.increment(callChanges)
                            .andThen(Single.just(callsBatch));
                })
                .subscribe(callsBatch -> {
                    inFlightRequests -= callsBatch.size();
                }, err -> {
                    // TODO: figure out how to handle this properly with respect to inFlightRequests and graceful
                    //  shutdown
                    log.error("failed to persist calls batch", err);
                });
    }
}
