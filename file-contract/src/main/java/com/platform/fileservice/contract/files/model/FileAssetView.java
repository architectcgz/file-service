package com.platform.fileservice.contract.files.model;

/**
 * Public file metadata view exposed by v2 APIs.
 */
public record FileAssetView(
        String fileId,
        String tenantId,
        String ownerId,
        String originalFilename,
        String contentType,
        long fileSize,
        AccessLevelView accessLevel,
        String status
) {
}
