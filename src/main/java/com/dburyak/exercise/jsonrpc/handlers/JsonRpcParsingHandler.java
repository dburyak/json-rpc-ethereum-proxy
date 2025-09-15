package com.dburyak.exercise.jsonrpc.handlers;

import com.dburyak.exercise.jsonrpc.JsonRpcRequest;
import com.dburyak.exercise.jsonrpc.ProxiedReqCtx;
import com.dburyak.exercise.jsonrpc.ReqHandler;
import com.dburyak.exercise.jsonrpc.err.UnsupportedJsonRpcVersionException;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Handler that parses incoming JSON-RPC requests and populates the ProxiedReqCtx with the parsed data.
 */
public class JsonRpcParsingHandler implements ReqHandler {

    @Override
    public Maybe<ProxiedReqCtx> handle(ProxiedReqCtx reqCtx) {
        return reqCtx.getIncomingReqCtx().request().rxBody()
                .map(bodyBuf -> {
                    var bodyJson = bodyBuf.toJsonObject();
                    var jsonRpcVersion = bodyJson.getString(JsonRpcRequest.FIELD_VERSION);
                    if (!JsonRpcRequest.VERSION_2_0.equals(jsonRpcVersion)) {
                        throw new UnsupportedJsonRpcVersionException(jsonRpcVersion);
                    }
                    reqCtx.setJsonRpcRequest(new JsonRpcRequest(bodyBuf.toJsonObject()));
                    return reqCtx;
                })
                .toMaybe();
    }
}
