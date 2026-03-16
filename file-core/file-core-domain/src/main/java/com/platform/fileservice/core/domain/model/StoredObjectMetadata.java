package com.platform.fileservice.core.domain.model;

/**
 * Object metadata observed from storage after a direct upload.
 */
public record StoredObjectMetadata(
        long sizeBytes,
        String contentType
) {
}
