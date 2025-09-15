package com.dburyak.exercise.jsonrpc;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class JsonRpcRequest {
    public static final String FIELD_VERSION = "jsonrpc";
    public static final String VERSION_2_0 = "2.0";
    public static final String FIELD_METHOD = "method";
    public static final String FIELD_ID = "id";

    JsonObject fullRequest;
    String method;
    Object id; // can be String, Number or null
    // Other properties (including any calculated ones) can be added here as needed

    public JsonRpcRequest(JsonObject fullRequest) {
        this.fullRequest = fullRequest;
        this.method = fullRequest.getString(FIELD_METHOD);
        this.id = fullRequest.getValue(FIELD_ID);
    }
}
