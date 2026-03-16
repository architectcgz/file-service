package com.platform.fileservice.contract.upload.model;

/**
 * View for one multipart upload URL.
 */
public record PartUploadUrlView(
        int partNumber,
        String uploadUrl,
        int expiresInSeconds
) {
}
