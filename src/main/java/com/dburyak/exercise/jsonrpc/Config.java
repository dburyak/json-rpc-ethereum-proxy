package com.dburyak.exercise.jsonrpc;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Value;

import java.util.Arrays;
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
    public static final String PROXIED_BACKEND_URLS_ENV = CFG_PREFIX_ENV + "PROXIED_BACKEND_URLS";

    public static final List<String> ALL_ENV_VARS = List.of(
            NUM_VERTICLES_ENV,
            PORT_ENV,
            API_PATH_ENV,
            PROXIED_BACKEND_URLS_ENV
    );

    int numVerticles;
    int port;
    String apiPath;
    List<String> proxiedBackendUrls;

    public Config(JsonObject cfgJson) {
        this.numVerticles = getInt(NUM_VERTICLES_ENV, NUM_VERTICLES, cfgJson,
                () -> Runtime.getRuntime().availableProcessors());
        this.port = getInt(PORT_ENV, PORT, cfgJson, () -> PORT_DEFAULT);
        this.apiPath = getString(API_PATH_ENV, API_PATH, cfgJson, () -> API_PATH_DEFAULT);
        var proxiedBackendUrls = getStringList(PROXIED_BACKEND_URLS_ENV, null, cfgJson);
        if (proxiedBackendUrls == null || proxiedBackendUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one proxied backend URL must be provided via env var "
                    + PROXIED_BACKEND_URLS_ENV);
        }
        this.proxiedBackendUrls = proxiedBackendUrls;
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

    private static List<String> getStringList(String envVarName, String cfgName, JsonObject cfgJson,
            Supplier<List<String>> defaultValue) {
        var envValue = cfgJson.getValue(envVarName);
        // Vertx attempts to parse env var values as JSON, i.e. value '["a","b"]' becomes a List, while comma-separated
        // string "a,b" stays a String as is. It's not very intuitive and not what people used to with the majority
        // of the applications/frameworks, so we handle both cases here.
        if (envValue != null) {
            return jsonValueToStringList(envValue);
        }
        if (cfgName != null) {
            var subCfg = cfgJson.getJsonObject(CFG_PREFIX);
            if (subCfg != null) {
                var cfgValue = subCfg.getValue(cfgName);
                if (cfgValue != null) {
                    return jsonValueToStringList(cfgValue);
                }
            }
        }
        return defaultValue.get();
    }

    private static List<String> jsonValueToStringList(Object value) {
        if (value instanceof JsonArray valueArr) {
            return valueArr.stream().map(Object::toString).toList();
        }
        return Arrays.stream(value.toString().split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> getStringList(String envVarName, String cfgName, JsonObject cfgJson) {
        return getStringList(envVarName, cfgName, cfgJson, () -> null);
    }
}
