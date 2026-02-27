package com.platform.fileservice.client.exception;

/**
 * Base exception for all File Service client errors.
 * All specific exceptions extend this class.
 */
public class FileServiceException extends RuntimeException {

    private final Integer statusCode;

    public FileServiceException(String message) {
        super(message);
        this.statusCode = null;
    }

    public FileServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public FileServiceException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public FileServiceException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
