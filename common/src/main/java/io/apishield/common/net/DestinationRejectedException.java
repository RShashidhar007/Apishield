package io.apishield.common.net;

/** Thrown when an upstream destination violates the destination policy (SSRF protection). */
public class DestinationRejectedException extends RuntimeException {
    private final String code;
    public DestinationRejectedException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
