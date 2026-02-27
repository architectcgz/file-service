package com.platform.fileservice.client.exception;

/**
 * Exception thrown when the server returns HTTP 403 Forbidden.
 * Indicates that the authenticated user does not have permission to access the resource.
 */
public class AccessDeniedException extends FileServiceException {

    public AccessDeniedException(String message) {
        super(message, 403);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, 403, cause);
    }
}
