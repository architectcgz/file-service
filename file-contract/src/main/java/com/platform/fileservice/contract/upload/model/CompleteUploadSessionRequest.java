package com.platform.fileservice.contract.upload.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Request payload for completing a multipart upload session.
 */
public record CompleteUploadSessionRequest(
        @NotBlank String contentType,
        List<@Valid CompletedUploadPartRequest> parts
) {
}
