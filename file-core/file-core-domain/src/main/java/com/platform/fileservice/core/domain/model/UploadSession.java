package com.platform.fileservice.core.domain.model;

import java.time.Instant;

/**
 * Unified upload session for inline, direct and presigned uploads.
 */
public record UploadSession(
        String uploadSessionId,
        String tenantId,
        String ownerId,
        UploadMode uploadMode,
        AccessLevel targetAccessLevel,
        String originalFilename,
        String contentType,
        long expectedSize,
        String fileHash,
        String objectKey,
        int chunkSizeBytes,
        int totalParts,
        String providerUploadId,
        String fileId,
        UploadSessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {

    public boolean isTerminal() {
        return status == UploadSessionStatus.COMPLETED
                || status == UploadSessionStatus.ABORTED
                || status == UploadSessionStatus.EXPIRED
                || status == UploadSessionStatus.FAILED;
    }

    public boolean hasMultipartUpload() {
        return objectKey != null
                && !objectKey.isBlank()
                && providerUploadId != null
                && !providerUploadId.isBlank();
    }

    public boolean hasCompletedFile() {
        return fileId != null && !fileId.isBlank();
    }
}
