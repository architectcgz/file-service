package com.platform.fileservice.core.domain.model;

/**
 * Client-reported multipart part receipt used when finalizing an upload session.
 */
public record CompletedUploadPart(
        int partNumber,
        String etag
) {
}
