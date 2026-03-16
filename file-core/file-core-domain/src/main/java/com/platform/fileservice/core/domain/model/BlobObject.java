package com.platform.fileservice.core.domain.model;

import java.time.Instant;

/**
 * Physical object stored in object storage and shared by one or more file assets.
 */
public record BlobObject(
        String blobObjectId,
        String tenantId,
        String storageProvider,
        String bucketName,
        String objectKey,
        String hashValue,
        String hashAlgorithm,
        long fileSize,
        String contentType,
        int referenceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
