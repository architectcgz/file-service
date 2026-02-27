package com.platform.fileservice.client.exception;

/**
 * Exception thrown when network-level errors occur.
 * This includes connection timeouts, connection refused, DNS resolution failures, etc.
 */
public class NetworkException extends FileServiceException {

    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
