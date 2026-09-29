package dev.golem.harness.api;

/** A command the client refused, a transport failure, or a failed expectation. */
public class HarnessException extends RuntimeException {

    public HarnessException(String message) {
        super(message);
    }

    public HarnessException(String message, Throwable cause) {
        super(message, cause);
    }
}
