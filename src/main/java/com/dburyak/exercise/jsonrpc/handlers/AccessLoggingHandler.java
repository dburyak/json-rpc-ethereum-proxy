package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.Config;
import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.Subject;
import io.reactivex.rxjava3.subjects.UnicastSubject;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Handler that logs access information for incoming requests.
 * <p>
 * IMPL NOTE: the easiest and most performant way to log access asynchronously (see README for details on "why") is
 * simply to configure the logger to use an async appender. However, this approach relies on the logging framework.
 * Potentially, this handler could use not a logging framework, but write access information to a database or any other
 * kind of storage or sink like syslog. That's why I implement both async handling here and using a DTO.
 */
@Log4j2
public class AccessLoggingHandler implements ReqHandler {
    // this matches logger name in log4j2.yaml
    private static final Logger ACCESS_LOG = LogManager.getLogger("ACCESS_LOG");
    private static final Duration BATCH_WRITE_INTERVAL = Duration.ofSeconds(1); // this could be configurable
    private final Duration gracefulShutdownTimeout;
    private final Subject<AccessLogEntry> accessLogEntries = UnicastSubject.create();

    // Vertx event-loop is single-threaded, and we create separate handler instance for each verticle, so we don't
    // need any concurrency control here
    private int inFlightOps = 0;
    private Disposable writerSubscription;

    public AccessLoggingHandler(Config cfg) {
        this.gracefulShutdownTimeout = cfg.getGracefulShutdownTimeout();
    }

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
        return Maybe.fromSupplier(() -> {
            if (writerSubscription == null) {
                // We can't start it from the constructor as it's called on a thread different from the EL of the
                // verticle (because handlers are created before deploying verticles in current design). Better
                // long-term solution would be to introduce AsyncStartable with "startAsync" (similar to AsyncCloseable)
                // and let each verticle to start its components during verticle startup each on its EL thread.
                startLogWriterHandler();
            }
            var logEntry = new AccessLogEntry(Instant.now(), reqCtx.getCallersIp(),
                    reqCtx.getJsonRpcRequest().getMethod());
            accessLogEntries.onNext(logEntry);
            inFlightOps++;
            return reqCtx;
        });
    }

    @Override
    public Completable closeAsync() {
        log.debug("closing, inFlightRequests={}", inFlightOps);
        if (inFlightOps <= 0) {
            return Completable.complete();
        }
        // there's a way to implement it with listeners/Promises without polling, but it's more complex and requires
        // more memory and CPU wasted on each request, so polling being ugly still is not a bad trade-off here
        return Observable.interval(0, 50, MILLISECONDS)
                .filter(ignr -> inFlightOps <= 0)
                .take(1)
                .ignoreElements()
                .timeout(gracefulShutdownTimeout.toMillis(), MILLISECONDS, Completable.complete());
    }

    @Value
    public static class AccessLogEntry {
        Instant timestamp;
        String ip;
        String method;
        // if any additional info is needed, we can capture it in ProxiedReqCtx, extract in this handler and add here

        @Override
        public String toString() {
            return String.format("%s - %s - %s", timestamp, ip, method);
        }
    }

    private void startLogWriterHandler() {
        log.debug("starting access log writer handler");
        writerSubscription = accessLogEntries.buffer(BATCH_WRITE_INTERVAL.toMillis(), MILLISECONDS)
                .filter(batch -> !batch.isEmpty())
                .subscribe(batch -> {
                    // If we need to switch access log storage implementations here, we would introduce an interface
                    // and use it here instead of direct logging.
                    for (var entry : batch) {
                        ACCESS_LOG.info(entry);
                        inFlightOps--;
                    }
                }, err -> log.error("unexpected error in access log writer handler, stopping the handler", err));
    }
}
