package com.platform.fileservice.core.domain.model;

import java.time.Instant;

/**
 * Logical file reference visible to business systems and end users.
 */
public record FileAsset(
        String fileId,
        String tenantId,
        String ownerId,
        String blobObjectId,
        String originalFilename,
        String objectKey,
        String contentType,
        long fileSize,
        AccessLevel accessLevel,
        FileAssetStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
