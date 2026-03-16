package com.platform.fileservice.contract.upload.model;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

/**
 * Client-supplied part confirmation used during complete requests.
 */
public record CompletedUploadPartRequest(
        @NotNull @Positive Integer partNumber,
        String etag
) {
}
