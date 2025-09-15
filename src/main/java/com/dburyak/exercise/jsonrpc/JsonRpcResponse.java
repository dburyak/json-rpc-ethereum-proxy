package com.dburyak.exercise.jsonrpc;

import com.dburyak.exercise.jsonrpc.err.ProxyPublicException;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Immutable representation of a JSON-RPC response. Most likely we'll be using it only for error responses, but let's
 * keep it generic for now.
 */
@Value
public class JsonRpcResponse {
    public static final String FIELD_VERSION = "jsonrpc";
    public static final String VERSION_2_0 = "2.0";
    public static final String FIELD_RESULT = "result";
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_ID = "id";

    JsonObject fullResponse;
    String version;
    JsonObject result; // not sure if it can be primitive or a JsonArray, fine for now
    Error error;
    Object id; // can be String, Number or null

    public JsonRpcResponse(JsonObject fullResponse) {
        this.fullResponse = fullResponse;
        this.version = fullResponse.getString(FIELD_VERSION);
        this.result = fullResponse.getJsonObject(FIELD_RESULT);
        var errObj = fullResponse.getJsonObject(FIELD_ERROR);
        if (this.result == null && errObj != null) {
            this.error = new Error(
                    errObj.getInteger(Error.FIELD_CODE),
                    errObj.getString(Error.FIELD_MESSAGE),
                    errObj.getJsonObject(Error.FIELD_DATA)
            );
        } else {
            this.error = null;
        }
        this.id = fullResponse.getValue(FIELD_ID);
    }

    public JsonObject toJson() {
        if (fullResponse != null) {
            return fullResponse;
        }
        var json = new JsonObject()
                .put(FIELD_VERSION, version)
                .put(FIELD_RESULT, result)
                .put(FIELD_ID, id);
        if (error != null) {
            json.put(FIELD_ERROR, error.toJson());
        }
        return json;
    }

    public JsonRpcResponse(JsonObject fullResp, Error err) {
        this.fullResponse = fullResp;
        this.version = fullResp.getString(FIELD_VERSION);
        this.result = fullResp.getJsonObject(FIELD_RESULT);
        this.error = err;
        this.id = fullResp.getValue(FIELD_ID);
    }

    public static JsonRpcResponse failed(ProxyPublicException err) {
        var errObj = new Error(err.getJsonRpcErrorCode(), err.getJsonRpcErrorMessage(), err.getJsonRpcErrorData());
        var fullResp = new JsonObject()
                .put(FIELD_VERSION, VERSION_2_0)
                .put(FIELD_ERROR, errObj.toJson())
                .put(FIELD_ID, err.getJsonRpcRequest().getId());
        return new JsonRpcResponse(fullResp, errObj);
    }

    @Value
    @RequiredArgsConstructor
    public static class Error {
        public static final String FIELD_CODE = "code";
        public static final String FIELD_MESSAGE = "message";
        public static final String FIELD_DATA = "data";

        int code;
        String message;
        JsonObject data;

        public Error(int code, String message) {
            this(code, message, null);
        }

        public JsonObject toJson() {
            var json = new JsonObject()
                    .put(FIELD_CODE, code)
                    .put(FIELD_MESSAGE, message);
            if (data != null) {
                json.put(FIELD_DATA, data);
            }
            return json;
        }
    }
}
