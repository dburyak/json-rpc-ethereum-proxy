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
    public static final String PORT_ENV = CFG_PREFIX_ENV + "PORT";
    public static final String PORT = "port";
    public static final int PORT_DEFAULT = 8080;
    public static final String API_PATH_ENV = CFG_PREFIX_ENV + "API_PATH";
    public static final String API_PATH = "apiPath";
    public static final String API_PATH_DEFAULT = "/";

    public static final List<String> ALL_ENV_VARS = List.of(
            NUM_VERTICLES_ENV
    );

    int numVerticles;
    int port;
    String apiPath;

    public Config(JsonObject cfgJson) {
        this.numVerticles = getInt(NUM_VERTICLES_ENV, NUM_VERTICLES, cfgJson,
                () -> Runtime.getRuntime().availableProcessors());
        this.port = getInt(PORT_ENV, PORT, cfgJson, () -> PORT_DEFAULT);
        this.apiPath = getString(API_PATH_ENV, API_PATH, cfgJson, () -> API_PATH_DEFAULT);
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

    private static String getString(String envVarName, String cfgName, JsonObject cfgJson,
            Supplier<String> defaultValue) {
        var envValue = cfgJson.getString(envVarName);
        if (envValue != null) {
            return envValue;
        }
        var subCfg = cfgJson.getJsonObject(CFG_PREFIX);
        if (subCfg != null) {
            var cfgValue = subCfg.getString(cfgName);
            if (cfgValue != null) {
                return cfgValue;
            }
        }
        return defaultValue.get();
    }
}
