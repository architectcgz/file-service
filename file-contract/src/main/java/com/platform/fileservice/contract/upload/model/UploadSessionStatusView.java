package com.platform.fileservice.contract.upload.model;

/**
 * Upload session lifecycle values exposed by v2 APIs.
 */
public enum UploadSessionStatusView {
    INITIATED,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    ABORTED,
    EXPIRED,
    FAILED
}
