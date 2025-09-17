package com.dburyak.exercise.jsonrpc;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Value;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Value
public class Config {
    public static final String CFG_PREFIX_ENV = "JSONRPC_";
    public static final String NUM_VERTICLES_ENV = CFG_PREFIX_ENV + "NUM_VERTICLES";
    public static final String PORT_ENV = CFG_PREFIX_ENV + "PORT";
    public static final String API_PATH_ENV = CFG_PREFIX_ENV + "API_PATH";
    public static final String PROXIED_BACKEND_URLS_ENV = CFG_PREFIX_ENV + "PROXIED_BACKEND_URLS";
    public static final String GRACEFUL_SHUTDOWN_TIMEOUT_ENV = CFG_PREFIX_ENV + "GRACEFUL_SHUTDOWN_TIMEOUT";
    public static final String GLOBAL_IP_RATE_LIMITING_ENABLED_ENV = CFG_PREFIX_ENV + "GLOBAL_IP_RATE_LIMITING_ENABLED";
    public static final String PER_METHOD_IP_RATE_LIMITING_ENABLED_ENV =
            CFG_PREFIX_ENV + "PER_METHOD_IP_RATE_LIMITING_ENABLED";
    public static final String ACCESS_LOG_ENABLED_ENV = CFG_PREFIX_ENV + "ACCESS_LOG_ENABLED";
    public static final String TLS_ENABLED_ENV = CFG_PREFIX_ENV + "TLS_ENABLED";
    public static final String TLS_P12_PATH_ENV = CFG_PREFIX_ENV + "TLS_P12_PATH";
    public static final String TLS_P12_PASSWORD_ENV = CFG_PREFIX_ENV + "TLS_P12_PASSWORD";
    public static final List<String> ALL_ENV_VARS = List.of(
            NUM_VERTICLES_ENV,
            PORT_ENV,
            API_PATH_ENV,
            PROXIED_BACKEND_URLS_ENV,
            GRACEFUL_SHUTDOWN_TIMEOUT_ENV,
            GLOBAL_IP_RATE_LIMITING_ENABLED_ENV,
            PER_METHOD_IP_RATE_LIMITING_ENABLED_ENV,
            ACCESS_LOG_ENABLED_ENV,
            TLS_ENABLED_ENV,
            TLS_P12_PATH_ENV,
            TLS_P12_PASSWORD_ENV
    );

    private static final String CFG_PREFIX = "jsonrpc";
    private static final String NUM_VERTICLES = "numVerticles";
    private static final String PORT = "port";
    private static final int PORT_DEFAULT = 8080;
    private static final String API_PATH = "apiPath";
    private static final String API_PATH_DEFAULT = "/";
    private static final String GRACEFUL_SHUTDOWN_TIMEOUT = "gracefulShutdownTimeout";
    private static final String GRACEFUL_SHUTDOWN_TIMEOUT_DEFAULT_STR = "60s";
    private static final String GLOBAL_IP_RATE_LIMITING = "globalIpRateLimiting";
    private static final String PER_METHOD_IP_RATE_LIMITING = "perMethodIpRateLimiting";
    private static final String ENABLED = "enabled";
    private static final String REQUESTS = "requests";
    private static final String TIME_WINDOW = "timeWindow";
    private static final String LOCAL_CACHE_SIZE = "localCacheSize";
    private static final String METHODS = "methods";
    private static final String CALL_TRACKING_API_PATH = "callTrackingApiPath";
    private static final String ACCESS_LOG_ENABLED = "accessLogEnabled";
    private static final String TLS_ENABLED = "tlsEnabled";


    int numVerticles;
    int port;
    String apiPath;
    List<String> proxiedBackendUrls;
    Duration gracefulShutdownTimeout;
    GlobalIpRateLimiting globalIpRateLimiting;
    PerMethodIpRateLimiting perMethodIpRateLimiting;
    String callTrackingApiPath;
    boolean accessLogEnabled;
    boolean tlsEnabled;
    String tlsP12Path;
    String tlsP12Password;

    public Config(JsonObject cfgRootJson) {
        var cfgProxyJson = cfgRootJson.getJsonObject(CFG_PREFIX);
        this.numVerticles = getInt(NUM_VERTICLES_ENV, cfgRootJson, NUM_VERTICLES, cfgProxyJson,
                () -> Runtime.getRuntime().availableProcessors());
        this.port = getInt(PORT_ENV, cfgRootJson, PORT, cfgProxyJson, () -> PORT_DEFAULT);
        this.apiPath = getString(API_PATH_ENV, cfgRootJson, API_PATH, cfgProxyJson, () -> API_PATH_DEFAULT);
        var proxiedBackendUrls = getStringList(PROXIED_BACKEND_URLS_ENV, cfgRootJson, null, null);
        if (proxiedBackendUrls == null || proxiedBackendUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one proxied backend URL must be provided via env var "
                    + PROXIED_BACKEND_URLS_ENV);
        }
        this.proxiedBackendUrls = proxiedBackendUrls;
        var gracefulShutdownTimeoutStr = getString(GRACEFUL_SHUTDOWN_TIMEOUT_ENV, cfgRootJson,
                GRACEFUL_SHUTDOWN_TIMEOUT, cfgProxyJson, () -> GRACEFUL_SHUTDOWN_TIMEOUT_DEFAULT_STR);
        this.gracefulShutdownTimeout = parseDuration(gracefulShutdownTimeoutStr);
        var globalIpRateLmtlCfgJson = cfgProxyJson != null ? cfgProxyJson.getJsonObject(GLOBAL_IP_RATE_LIMITING) : null;
        this.globalIpRateLimiting = new GlobalIpRateLimiting(
                getBoolean(GLOBAL_IP_RATE_LIMITING_ENABLED_ENV, cfgRootJson, ENABLED, globalIpRateLmtlCfgJson,
                        () -> false),
                getInt(null, null, REQUESTS, globalIpRateLmtlCfgJson, () -> 5_000),
                parseDuration(getString(null, null, TIME_WINDOW, globalIpRateLmtlCfgJson, () -> "1m")),
                getInt(null, null, LOCAL_CACHE_SIZE, globalIpRateLmtlCfgJson, () -> 5_000)
        );
        this.perMethodIpRateLimiting = parsePerMethodIpRateLmtCfg(cfgRootJson);
        this.callTrackingApiPath = getString(null, null, CALL_TRACKING_API_PATH, cfgProxyJson,
                () -> "/call-tracking");
        this.accessLogEnabled = getBoolean(ACCESS_LOG_ENABLED_ENV, cfgRootJson, ACCESS_LOG_ENABLED, cfgProxyJson,
                () -> true);
        this.tlsEnabled = getBoolean(TLS_ENABLED_ENV, cfgRootJson, TLS_ENABLED, cfgProxyJson, () -> false);
        var tlsP12Path = getString(TLS_P12_PATH_ENV, cfgRootJson, null, null, () -> null);
        var tlsP12Password = getString(TLS_P12_PASSWORD_ENV, cfgRootJson, null, null, () -> null);
        if (tlsEnabled && ((tlsP12Path == null || tlsP12Path.isEmpty()) || tlsP12Password == null)) {
            throw new IllegalArgumentException("TLS p12 path and password must be provided via " +
                    TLS_P12_PATH_ENV + " and " + TLS_P12_PASSWORD_ENV +
                    " env vars when TLS is enabled");
        }
        this.tlsP12Path = tlsP12Path;
        this.tlsP12Password = tlsP12Password;
    }

    @Value
    public static class GlobalIpRateLimiting {
        boolean enabled;
        int requests;
        Duration timeWindow;
        int localCacheSize;

        public GlobalIpRateLimiting(boolean enabled, int requests, Duration timeWindow, int localCacheSize) {
            if (requests <= 0) {
                throw new IllegalArgumentException("requests must be > 0");
            }
            if (timeWindow.isNegative() || timeWindow.isZero()) {
                throw new IllegalArgumentException("timeWindow must be > 0");
            }
            if (localCacheSize <= 0) {
                throw new IllegalArgumentException("localCacheSize must be > 0");
            }
            this.enabled = enabled;
            this.requests = requests;
            this.timeWindow = timeWindow;
            this.localCacheSize = localCacheSize;
        }
    }

    @Value
    public static class PerMethodIpRateLimiting {
        boolean enabled;
        int localCacheSize;
        Map<String, MethodCfg> methodCfgs;

        public PerMethodIpRateLimiting(boolean enabled, int localCacheSize, Map<String, MethodCfg> methodCfgs) {
            if (localCacheSize <= 0) {
                throw new IllegalArgumentException("localCacheSize must be > 0");
            }
            if (methodCfgs == null || methodCfgs.isEmpty()) {
                throw new IllegalArgumentException("At least one method configuration must be provided");
            }
            this.enabled = enabled;
            this.localCacheSize = localCacheSize;
            this.methodCfgs = methodCfgs;
        }

        @Value
        public static class MethodCfg {
            String method;
            int requests;
            Duration timeWindow;

            public MethodCfg(String method, int requests, Duration timeWindow) {
                if (method == null || method.isEmpty()) {
                    throw new IllegalArgumentException("method must be provided");
                }
                if (requests <= 0) {
                    throw new IllegalArgumentException("requests must be > 0");
                }
                if (timeWindow.isNegative() || timeWindow.isZero()) {
                    throw new IllegalArgumentException("timeWindow must be > 0");
                }
                this.method = method;
                this.requests = requests;
                this.timeWindow = timeWindow;
            }
        }
    }

    private static PerMethodIpRateLimiting parsePerMethodIpRateLmtCfg(JsonObject cfgRootJson) {
        var cfgProxyJson = cfgRootJson.getJsonObject(CFG_PREFIX);
        var perMtdIpRtlmtCfgJson = cfgProxyJson != null ? cfgProxyJson.getJsonObject(PER_METHOD_IP_RATE_LIMITING)
                : null;
        var enabled = getBoolean(PER_METHOD_IP_RATE_LIMITING_ENABLED_ENV, cfgRootJson, ENABLED, perMtdIpRtlmtCfgJson,
                () -> false);
        var localCacheSize = getInt(null, null, LOCAL_CACHE_SIZE, perMtdIpRtlmtCfgJson, () -> 5_000);
        var methodsCfgJson = perMtdIpRtlmtCfgJson != null ? perMtdIpRtlmtCfgJson.getJsonObject(METHODS) : null;
        var methodsCfgMap = methodsCfgJson.stream()
                .map(e -> {
                    var method = e.getKey();
                    var methodCfgJson = (JsonObject) e.getValue();
                    var requests = getInt(null, null, REQUESTS, methodCfgJson, () -> {
                        throw new IllegalArgumentException(REQUESTS + " must be provided for " +
                                PER_METHOD_IP_RATE_LIMITING + " method " + method);
                    });
                    var timeWindow = parseDuration(getString(null, null, TIME_WINDOW, methodCfgJson, () -> {
                        throw new IllegalArgumentException(TIME_WINDOW + " must be provided for " +
                                PER_METHOD_IP_RATE_LIMITING + " method " + method);
                    }));
                    return new PerMethodIpRateLimiting.MethodCfg(method, requests, timeWindow);
                })
                .collect(Collectors.toMap(PerMethodIpRateLimiting.MethodCfg::getMethod, m -> m));
        return new PerMethodIpRateLimiting(enabled, localCacheSize, methodsCfgMap);
    }

    private static int getInt(String envVarName, JsonObject cfgJson, String cfgName, JsonObject subCfgJson,
            Supplier<Integer> defaultValue) {
        if (envVarName != null) {
            var envValue = cfgJson.getInteger(envVarName);
            if (envValue != null) {
                return envValue;
            }
        }
        if (subCfgJson != null && cfgName != null) {
            var cfgValue = subCfgJson.getInteger(cfgName);
            if (cfgValue != null) {
                return cfgValue;
            }
        }
        return defaultValue.get();
    }

    private static String getString(String envVarName, JsonObject cfgJson, String cfgName, JsonObject subCfgJson,
            Supplier<String> defaultValue) {
        if (envVarName != null) {
            var envValue = cfgJson.getString(envVarName);
            if (envValue != null) {
                return envValue;
            }
        }
        if (subCfgJson != null && cfgName != null) {
            var cfgValue = subCfgJson.getString(cfgName);
            if (cfgValue != null) {
                return cfgValue;
            }
        }
        return defaultValue.get();
    }

    private static boolean getBoolean(String envVarName, JsonObject cfgJson, String cfgName, JsonObject subCfgJson,
            Supplier<Boolean> defaultValue) {
        if (envVarName != null) {
            var envValue = cfgJson.getBoolean(envVarName);
            if (envValue != null) {
                return envValue;
            }
        }
        if (subCfgJson != null && cfgName != null) {
            var cfgValue = subCfgJson.getBoolean(cfgName);
            if (cfgValue != null) {
                return cfgValue;
            }
        }
        return defaultValue.get();
    }

    private static boolean getBoolean(String envVarName, JsonObject cfgJson, String cfgName, JsonObject subCfgJson) {
        return getBoolean(envVarName, cfgJson, cfgName, subCfgJson, () -> false);
    }

    private static List<String> getStringList(String envVarName, JsonObject cfgJson, String cfgName,
            JsonObject subCfgJson, Supplier<List<String>> defaultValue) {
        if (envVarName != null) {
            var envValue = cfgJson.getValue(envVarName);
            // Vertx attempts to parse env var values as JSON, i.e. value '["a","b"]' becomes a List, while
            // comma-separated
            // string "a,b" stays a String as is. It's not very intuitive and not what people used to with the majority
            // of the applications/frameworks, so we handle both cases here.
            if (envValue != null) {
                return jsonValueToStringList(envValue);
            }
        }
        if (subCfgJson != null && cfgName != null) {
            var cfgValue = subCfgJson.getValue(cfgName);
            if (cfgValue != null) {
                return jsonValueToStringList(cfgValue);
            }
        }
        return defaultValue.get();
    }

    private static List<String> getStringList(String envVarName, JsonObject cfgJson, String cfgName,
            JsonObject subCfgJson) {
        return getStringList(envVarName, cfgJson, cfgName, subCfgJson, () -> null);
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

    private static Duration parseDuration(String durationStr) {
        // for simple cases this should work, e.g. "60s", "5m", "1h", "2h30m", "1h15m10s"
        return Duration.parse("PT" + durationStr);
    }
}
