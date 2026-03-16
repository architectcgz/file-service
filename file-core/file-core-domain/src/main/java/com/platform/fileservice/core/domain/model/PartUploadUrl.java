package com.platform.fileservice.core.domain.model;

/**
 * Presigned upload URL for one multipart part.
 */
public record PartUploadUrl(
        int partNumber,
        String uploadUrl,
        int expiresInSeconds
) {
}
