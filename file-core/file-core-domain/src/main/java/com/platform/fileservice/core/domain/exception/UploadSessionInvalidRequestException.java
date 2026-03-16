package com.platform.fileservice.core.domain.exception;

/**
 * Raised when an upload-session request violates domain constraints.
 */
public class UploadSessionInvalidRequestException extends RuntimeException {

    public UploadSessionInvalidRequestException(String message) {
        super(message);
    }
}
