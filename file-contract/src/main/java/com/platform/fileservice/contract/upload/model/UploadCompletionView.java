package com.platform.fileservice.contract.upload.model;

/**
 * Completion result returned after a v2 upload session is finalized.
 */
public record UploadCompletionView(
        String uploadSessionId,
        String fileId,
        UploadSessionStatusView status
) {
}
