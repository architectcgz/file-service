package com.platform.fileservice.contract.upload.model;

/**
 * Authoritative multipart part state returned by v2 upload APIs.
 */
public record UploadedPartView(
        int partNumber,
        String etag,
        long sizeBytes
) {
}
