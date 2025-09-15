package com.dburyak.exercise.jsonrpc.err;

import com.dburyak.exercise.jsonrpc.JsonRpcRequest;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

/**
 * Root exception for all exceptions that can be converted and propagated directly to the calling client.
 */
@Getter
public class ProxyPublicException extends RuntimeException {
    private final JsonRpcRequest jsonRpcRequest;
    private final int httpStatusCode;
    private final int jsonRpcErrorCode;
    private final String jsonRpcErrorMessage;
    private final JsonObject jsonRpcErrorData;

    /**
     * Constructor for errors that are not supposed to be translated to JSON-RPC errors (i.e. generic errors like
     * timeouts, connection errors, etc.).
     */
    public ProxyPublicException(int httpStatusCode, String message) {
        super(message);
        this.jsonRpcRequest = null;
        this.httpStatusCode = httpStatusCode;
        this.jsonRpcErrorCode = -1;
        this.jsonRpcErrorMessage = null;
        this.jsonRpcErrorData = null;
    }

    /**
     * Constructor for errors that are not supposed to be translated to JSON-RPC errors (i.e. generic errors like
     * timeouts, connection errors, etc.).
     */
    public ProxyPublicException(int httpStatusCode, String message, Throwable cause) {
        super(message, cause);
        this.jsonRpcRequest = null;
        this.httpStatusCode = httpStatusCode;
        this.jsonRpcErrorCode = -1;
        this.jsonRpcErrorMessage = null;
        this.jsonRpcErrorData = null;
    }

    public ProxyPublicException(JsonRpcRequest jsonRpcRequest, int httpStatusCode, int jsonRpcErrorCode,
            String jsonRpcErrorMessage, JsonObject jsonRpcErrorData, Throwable cause) {
        super(jsonRpcErrorMessage, cause);
        this.jsonRpcRequest = jsonRpcRequest;
        this.httpStatusCode = httpStatusCode;
        this.jsonRpcErrorCode = jsonRpcErrorCode;
        this.jsonRpcErrorMessage = jsonRpcErrorMessage;
        this.jsonRpcErrorData = jsonRpcErrorData;
    }

    public ProxyPublicException(JsonRpcRequest jsonRpcRequest, int httpStatusCode, int jsonRpcErrorCode,
            String jsonRpcErrorMessage, JsonObject jsonRpcErrorData) {
        this(jsonRpcRequest, httpStatusCode, jsonRpcErrorCode, jsonRpcErrorMessage, jsonRpcErrorData, null);
    }
}
