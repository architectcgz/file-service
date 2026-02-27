package com.platform.fileservice.client.exception;

/**
 * Exception thrown when the server returns HTTP 401 Unauthorized.
 * Indicates that authentication failed or the token is invalid/expired.
 */
public class AuthenticationException extends FileServiceException {

    public AuthenticationException(String message) {
        super(message, 401);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, 401, cause);
    }
}
