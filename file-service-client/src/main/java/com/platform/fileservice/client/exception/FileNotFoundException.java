package com.platform.fileservice.client.exception;

/**
 * Exception thrown when the server returns HTTP 404 Not Found.
 * Indicates that the requested file does not exist.
 */
public class FileNotFoundException extends FileServiceException {

    public FileNotFoundException(String message) {
        super(message, 404);
    }

    public FileNotFoundException(String message, Throwable cause) {
        super(message, 404, cause);
    }
}
