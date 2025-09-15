package com.dburyak.exercise.jsonrpc.err;

public class UnsupportedJsonRpcVersionException extends ProxyPublicException {

    public UnsupportedJsonRpcVersionException(String badVersion) {
        super(400, "Unsupported JSON-RPC version: " + badVersion);
    }
}
