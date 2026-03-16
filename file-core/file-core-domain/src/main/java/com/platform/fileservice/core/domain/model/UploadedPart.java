package com.platform.fileservice.core.domain.model;

/**
 * Authoritative multipart part state observed from object storage.
 */
public record UploadedPart(
        int partNumber,
        String etag,
        long sizeBytes
) {
}
