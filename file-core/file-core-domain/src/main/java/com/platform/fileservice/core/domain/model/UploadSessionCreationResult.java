package com.platform.fileservice.core.domain.model;

import java.util.List;

/**
 * Result of creating or reusing an upload session.
 */
public record UploadSessionCreationResult(
        UploadSession uploadSession,
        boolean resumed,
        boolean instantUpload,
        List<UploadedPart> uploadedParts
) {
}
