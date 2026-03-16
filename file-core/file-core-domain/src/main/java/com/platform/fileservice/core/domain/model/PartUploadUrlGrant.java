package com.platform.fileservice.core.domain.model;

import java.util.List;

/**
 * Batch of multipart part URLs issued for a single upload session.
 */
public record PartUploadUrlGrant(
        String uploadSessionId,
        List<PartUploadUrl> partUrls
) {
}
