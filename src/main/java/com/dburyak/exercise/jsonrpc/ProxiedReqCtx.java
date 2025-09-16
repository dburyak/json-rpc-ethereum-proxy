package com.dburyak.exercise.jsonrpc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import lombok.Data;

/**
 * Mutable context of a proxied request that is passed between different request handlers. Contains all the relevant
 * data. Thanks to Vertx's single-threaded event loop model, we don't need to worry about concurrent access to this
 * object.
 */
@Data
public class ProxiedReqCtx {
    private final RoutingContext incomingReqCtx;
    private String callersIp;
    private Buffer incomingReqBodyBuf;
    private JsonObject incomingReqBodyJson;
    private HttpResponse<Buffer> backendResp;
    private JsonRpcRequest jsonRpcRequest;
}
