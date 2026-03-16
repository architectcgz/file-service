package com.platform.fileservice.contract.upload.model;

import com.platform.fileservice.contract.files.model.AccessLevelView;

import java.time.Instant;

/**
 * Upload session view exposed by v2 APIs.
 */
public record UploadSessionView(
        String uploadSessionId,
        String tenantId,
        String ownerId,
        UploadModeView uploadMode,
        AccessLevelView accessLevel,
        String originalFilename,
        String contentType,
        long expectedSize,
        String fileHash,
        int chunkSizeBytes,
        int totalParts,
        String fileId,
        UploadSessionStatusView status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
}
