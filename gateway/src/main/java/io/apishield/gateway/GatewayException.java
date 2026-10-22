package io.apishield.gateway;

import org.springframework.http.HttpStatus;

/** A deliberate gateway rejection. 'outcome' feeds analytics/threat rules; 'reason' is internal detail never shown to clients. */
public class GatewayException extends RuntimeException {
    private final HttpStatus status; private final String code; private final String outcome; private final String reason;
    public GatewayException(HttpStatus status, String code, String message, String outcome, String reason) {
        super(message, null, false, false); // no stack trace: control-flow exception
        this.status = status; this.code = code; this.outcome = outcome; this.reason = reason;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String outcome() { return outcome; }
    public String reason() { return reason; }
}
