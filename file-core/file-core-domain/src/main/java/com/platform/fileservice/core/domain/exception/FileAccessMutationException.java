package com.platform.fileservice.core.domain.exception;

/**
 * Raised when file access policy mutation cannot be persisted safely.
 */
public class FileAccessMutationException extends RuntimeException {

    public FileAccessMutationException(String message) {
        super(message);
    }

    public FileAccessMutationException(String message, Throwable cause) {
        super(message, cause);
    }
}
