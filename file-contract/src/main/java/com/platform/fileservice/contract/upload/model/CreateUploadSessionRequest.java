package com.platform.fileservice.contract.upload.model;

import com.platform.fileservice.contract.files.model.AccessLevelView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for creating a v2 upload session.
 */
public record CreateUploadSessionRequest(
        @NotNull UploadModeView uploadMode,
        @NotNull AccessLevelView accessLevel,
        @NotBlank String originalFilename,
        @NotBlank String contentType,
        @Positive long expectedSize,
        String fileHash
) {
}
