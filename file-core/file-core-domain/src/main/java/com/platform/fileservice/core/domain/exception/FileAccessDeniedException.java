package com.platform.fileservice.core.domain.exception;

/**
 * Raised when a subject is not allowed to access a file asset.
 */
public class FileAccessDeniedException extends RuntimeException {

    public FileAccessDeniedException(String message) {
        super(message);
    }
}
