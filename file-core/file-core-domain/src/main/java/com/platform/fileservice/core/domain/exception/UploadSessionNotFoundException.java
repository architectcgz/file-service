package com.platform.fileservice.core.domain.exception;

/**
 * Raised when an upload session is not visible in the requested tenant scope.
 */
public class UploadSessionNotFoundException extends RuntimeException {

    public UploadSessionNotFoundException(String message) {
        super(message);
    }
}
