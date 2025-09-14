package com.dburyak.exercise.jsonrpc;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class Config {
    public static final String CFG_PREFIX_ENV = "JSONRPC_";
    public static final String CFG_PREFIX = "jsonrpc.";
    public static final String NUM_VERTICLES_ENV = CFG_PREFIX_ENV + "NUM_VERTICLES";
    public static final String NUM_VERTICLES = "numVerticles";

    int numVerticles;

    public Config(JsonObject cfgJson) {
        this.numVerticles = cfgJson.getInteger(NUM_VERTICLES_ENV,
                cfgJson.getInteger(NUM_VERTICLES, Runtime.getRuntime().availableProcessors()));
    }
}
