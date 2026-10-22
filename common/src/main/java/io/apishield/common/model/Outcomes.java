package io.apishield.common.model;

/** Outcome codes recorded by the gateway for every request. */
public final class Outcomes {
    private Outcomes() {}
    public static final String OK = "OK";
    public static final String UPSTREAM_ERROR = "UPSTREAM_ERROR";
    public static final String UPSTREAM_TIMEOUT = "UPSTREAM_TIMEOUT";
    public static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String RATE_LIMIT_UNAVAILABLE = "RATE_LIMIT_UNAVAILABLE";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String DESTINATION_BLOCKED = "DESTINATION_BLOCKED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
