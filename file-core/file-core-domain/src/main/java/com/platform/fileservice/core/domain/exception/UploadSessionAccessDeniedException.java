package com.platform.fileservice.core.domain.exception;

/**
 * Raised when a subject is not allowed to inspect or mutate an upload session.
 */
public class UploadSessionAccessDeniedException extends RuntimeException {

    public UploadSessionAccessDeniedException(String message) {
        super(message);
    }
}
