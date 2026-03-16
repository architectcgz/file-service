package com.platform.fileservice.core.domain.model;

/**
 * Write-model view used when mutating file access policy.
 */
public record FileAccessMutationContext(
        FileAsset fileAsset,
        BlobObject blobObject
) {

    public boolean hasBoundBlobObject() {
        return blobObject != null;
    }
}
