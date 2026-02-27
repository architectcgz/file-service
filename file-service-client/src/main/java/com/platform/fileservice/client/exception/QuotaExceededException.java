package com.platform.fileservice.client.exception;

/**
 * Exception thrown when the server returns HTTP 413 Payload Too Large.
 * Indicates that the file size exceeds the allowed quota or limit.
 */
public class QuotaExceededException extends FileServiceException {

    public QuotaExceededException(String message) {
        super(message, 413);
    }

    public QuotaExceededException(String message, Throwable cause) {
        super(message, 413, cause);
    }
}
