package com.platform.fileservice.core.domain.model;

/**
 * Single-object upload URL issued for a presigned upload session.
 */
public record SingleUploadUrlGrant(
        String uploadSessionId,
        String uploadUrl,
        int expiresInSeconds
) {
}
