package com.dburyak.exercise.jsonrpc.entity;

import io.vertx.core.json.JsonObject;
import lombok.Value;

import java.util.Map;

@Value
public class CallsOfUser {
    String ip;
    Map<String, CallStats> methods;

    @Value
    public static class CallStats {
        long successfulCalls;
        long failedCalls;
    }

    public JsonObject toJson() {
        return JsonObject.mapFrom(this);
    }
}
