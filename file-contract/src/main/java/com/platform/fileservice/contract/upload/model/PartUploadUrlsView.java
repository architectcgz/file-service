package com.platform.fileservice.contract.upload.model;

import java.util.List;

/**
 * Multipart upload URL batch exposed by v2 APIs.
 */
public record PartUploadUrlsView(
        String uploadSessionId,
        List<PartUploadUrlView> partUrls
) {
}
