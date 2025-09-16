package com.dburyak.exercise.jsonrpc;

import lombok.Value;

@Value
public class TrackedCall {
    String ip;
    String method;
    long successfulCalls;
    long failedCalls;

    /**
     * Represents a change in the number of successful and failed calls for a specific IP and method. Whereas
     * TrackedCall represents the total (accumulated) counts, Change represents the delta (incremental) counts as a
     * result of one or more calls.
     */
    @Value
    public static class Change {
        String ip;
        String method;
        long successfulCalls;
        long failedCalls;
    }
}
