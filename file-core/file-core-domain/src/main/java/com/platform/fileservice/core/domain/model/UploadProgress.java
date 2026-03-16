package com.platform.fileservice.core.domain.model;

import java.util.List;

/**
 * Aggregated multipart upload progress for one upload session.
 */
public record UploadProgress(
        String uploadSessionId,
        int totalParts,
        int completedParts,
        long uploadedBytes,
        long totalBytes,
        int percentage,
        List<UploadedPart> uploadedParts
) {
}
