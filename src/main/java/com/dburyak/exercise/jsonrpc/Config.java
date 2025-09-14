package com.dburyak.exercise.jsonrpc;

import io.vertx.core.json.JsonObject;
import lombok.Value;

import java.util.List;
import java.util.function.Supplier;

@Value
public class Config {
    public static final String CFG_PREFIX_ENV = "JSONRPC_";
    public static final String CFG_PREFIX = "jsonrpc";

    public static final String NUM_VERTICLES_ENV = CFG_PREFIX_ENV + "NUM_VERTICLES";
    public static final String NUM_VERTICLES = "numVerticles";

    public static final List<String> ALL_ENV_VARS = List.of(
            NUM_VERTICLES_ENV
    );

    int numVerticles;

    public Config(JsonObject cfgJson) {
        this.numVerticles = getInt(NUM_VERTICLES_ENV, NUM_VERTICLES, cfgJson,
                () -> Runtime.getRuntime().availableProcessors());
    }

    private static int getInt(String envVarName, String cfgName, JsonObject cfgJson, Supplier<Integer> defaultValue) {
        var envValue = cfgJson.getInteger(envVarName);
        if (envValue != null) {
            return envValue;
        }
        var subCfg = cfgJson.getJsonObject(CFG_PREFIX);
        if (subCfg != null) {
            var cfgValue = subCfg.getInteger(cfgName);
            if (cfgValue != null) {
                return cfgValue;
            }
        }
        return defaultValue.get();
    }
}
