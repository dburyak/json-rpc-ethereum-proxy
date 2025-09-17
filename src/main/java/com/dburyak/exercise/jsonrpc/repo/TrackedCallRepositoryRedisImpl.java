package com.dburyak.exercise.jsonrpc.repo;

import com.dburyak.exercise.jsonrpc.entity.CallsOfUser;
import com.dburyak.exercise.jsonrpc.entity.CallsOfUser.CallStats;
import com.dburyak.exercise.jsonrpc.entity.TrackedCall;
import com.dburyak.exercise.jsonrpc.entity.TrackedCall.Change;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.redis.client.RedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static io.vertx.redis.client.Command.DEL;
import static io.vertx.redis.client.Command.HINCRBY;
import static io.vertx.redis.client.Command.HMGET;
import static io.vertx.redis.client.Command.HSCAN;
import static io.vertx.redis.client.Request.cmd;
import static java.util.Map.entry;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
public class TrackedCallRepositoryRedisImpl implements TrackedCallRepository {
    public static final String DELIMITER = ":";
    public static final String SUCCESS = "s";
    public static final String FAILURE = "f";
    private static final String PREFIX = "trck" + DELIMITER;
    private static final String SUCCESS_SUFFIX = DELIMITER + SUCCESS;
    private final RedisConnection redis;

    @Override
    public Completable increment(Collection<Change> calls) {
        var byIp = calls.stream().collect(groupingBy(Change::getIp));
        var reqs = byIp.entrySet().stream().flatMap(ipEntry -> {
            var ip = ipEntry.getKey();
            var methodSuccessInc = ipEntry.getValue().stream()
                    .collect(groupingBy(Change::getMethod, summingLong(Change::getSuccessfulCalls)));
            var methodFailureInc = ipEntry.getValue().stream()
                    .collect(groupingBy(Change::getMethod, summingLong(Change::getFailedCalls)));
            var successIncReqs = methodSuccessInc.entrySet().stream()
                    .filter(e -> e.getValue() > 0) // no need to increment by 0
                    .map(e ->
                            cmd(HINCRBY).arg(redisKey(ip))
                                    .arg(redisField(e.getKey(), SUCCESS))
                                    .arg(e.getValue()));
            var failureIncReqs = methodFailureInc.entrySet().stream()
                    .filter(e -> e.getValue() > 0) // no need to increment by 0
                    .map(e ->
                            cmd(HINCRBY).arg(redisKey(ip))
                                    .arg(redisField(e.getKey(), FAILURE))
                                    .arg(e.getValue()));
            return Stream.concat(successIncReqs, failureIncReqs);
        }).toList();
        return redis.rxBatch(reqs)
                .ignoreElement();
    }

    @Override
    public Maybe<TrackedCall> findByIpAndMethod(String ip, String method) {
        var successField = redisField(method, SUCCESS);
        var failureField = redisField(method, FAILURE);
        var req = cmd(HMGET).arg(redisKey(ip))
                .arg(successField)
                .arg(failureField);
        return redis.rxSend(req).flatMap(resp -> {
            var successfulCallsResp = resp.get(0);
            var failedCallsResp = resp.get(1);
            if (successfulCallsResp == null && failedCallsResp == null) {
                return Maybe.empty();
            }
            var successfulCalls = (successfulCallsResp != null) ? successfulCallsResp.toLong() : 0;
            var failedCalls = (failedCallsResp != null) ? failedCallsResp.toLong() : 0;
            return Maybe.just(new TrackedCall(ip, method, successfulCalls, failedCalls));
        });
    }

    @Override
    public Maybe<CallsOfUser> findByIp(String ip) {
        return hscan(redisKey(ip), 0L).toList().flatMapMaybe(batches -> {
            var callStatsMap = batches.stream().flatMap(r -> r.getValues().stream())
                    .map(e -> {
                        var field = e.getKey();
                        var method = field.substring(0, field.length() - SUCCESS_SUFFIX.length());
                        var isSuccess = field.endsWith(SUCCESS_SUFFIX);
                        var count = e.getValue();
                        // wish there were tuples in Java, it's late to add vavr at this point
                        return entry(method, isSuccess ? entry(SUCCESS, count) : entry(FAILURE, count));
                    })
                    .collect(groupingBy(Entry::getKey)) // by method
                    .entrySet().stream().map(e -> {
                        var method = e.getKey();
                        var successfulCalls = e.getValue().stream()
                                .filter(t -> SUCCESS.equals(t.getValue().getKey()))
                                .findFirst()
                                .map(t -> t.getValue().getValue()).orElse(0L);
                        var failedCalls = e.getValue().stream()
                                .filter(t -> FAILURE.equals(t.getValue().getKey()))
                                .findFirst()
                                .map(t -> t.getValue().getValue()).orElse(0L);
                        return new TrackedCall(ip, method, successfulCalls, failedCalls);
                    })
                    .collect(toMap(TrackedCall::getMethod,
                            c -> new CallStats(c.getSuccessfulCalls(), c.getFailedCalls())));
            return callStatsMap.isEmpty() ? Maybe.empty() : Maybe.just(new CallsOfUser(ip, callStatsMap));
        });
    }

    @Override
    public Single<Boolean> deleteByIp(String ip) {
        var delReq = cmd(DEL).arg(redisKey(ip));
        return redis.rxSend(delReq).toSingle()
                .map(resp -> resp.toInteger() > 0);
    }

    private Observable<HScanResult> hscan(String key, long cursor) {
        var req = cmd(HSCAN).arg(key).arg(cursor);
        return redis.rxSend(req).flatMapObservable(resp -> {
            var nextCursor = resp.get(0).toLong();
            var entries = resp.get(1);
            var values = new ArrayList<Map.Entry<String, Long>>(entries.size() / 2);
            for (var method : entries.getKeys()) {
                values.add(entry(method, entries.get(method).toLong()));
            }
            var scanResult = Observable.just(new HScanResult(nextCursor, values));
            return (nextCursor == 0L) ? scanResult : hscan(key, nextCursor).startWith(scanResult);
        });
    }

    @Value
    private static class HScanResult {
        long cursor;
        List<Map.Entry<String, Long>> values;
    }

    private String redisKey(String ip) {
        return PREFIX + ip;
    }

    private String redisField(String method, String status) {
        return method + DELIMITER + status;
    }
}
