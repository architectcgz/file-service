package com.platform.fileservice.client.exception;

/**
 * Exception thrown when the server returns HTTP 400 Bad Request.
 * Indicates that the request was malformed or contained invalid parameters.
 */
public class InvalidRequestException extends FileServiceException {

    public InvalidRequestException(String message) {
        super(message, 400);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, 400, cause);
    }
}
