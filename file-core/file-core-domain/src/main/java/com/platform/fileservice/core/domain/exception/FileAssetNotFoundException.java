package com.platform.fileservice.core.domain.exception;

/**
 * Raised when a file asset cannot be found in the requested tenant scope.
 */
public class FileAssetNotFoundException extends RuntimeException {

    public FileAssetNotFoundException(String message) {
        super(message);
    }
}
