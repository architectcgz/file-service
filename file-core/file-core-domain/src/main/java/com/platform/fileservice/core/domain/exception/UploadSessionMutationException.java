package com.platform.fileservice.core.domain.exception;

/**
 * Raised when upload session state cannot be persisted safely.
 */
public class UploadSessionMutationException extends RuntimeException {

    public UploadSessionMutationException(String message) {
        super(message);
    }

    public UploadSessionMutationException(String message, Throwable cause) {
        super(message, cause);
    }
}
