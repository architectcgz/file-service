package com.platform.fileservice.contract.upload.model;

import java.util.List;

/**
 * Progress view for one upload session.
 */
public record UploadProgressView(
        String uploadSessionId,
        int totalParts,
        int completedParts,
        long uploadedBytes,
        long totalBytes,
        int percentage,
        List<Integer> completedPartNumbers,
        List<UploadedPartView> completedPartInfos
) {
}
