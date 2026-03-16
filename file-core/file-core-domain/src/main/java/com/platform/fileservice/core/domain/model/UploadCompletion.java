package com.platform.fileservice.core.domain.model;

/**
 * Result of completing one upload session into a logical file asset.
 */
public record UploadCompletion(
        String uploadSessionId,
        String fileId,
        UploadSessionStatus status
) {
}
