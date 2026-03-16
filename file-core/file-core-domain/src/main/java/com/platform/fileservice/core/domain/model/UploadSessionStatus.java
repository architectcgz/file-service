package com.platform.fileservice.core.domain.model;

/**
 * Lifecycle state of an upload session.
 */
public enum UploadSessionStatus {
    INITIATED,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    ABORTED,
    EXPIRED,
    FAILED
}
