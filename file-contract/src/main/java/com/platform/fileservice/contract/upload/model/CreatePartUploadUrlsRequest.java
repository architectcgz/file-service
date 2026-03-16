package com.platform.fileservice.contract.upload.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Request payload for issuing multipart upload URLs.
 */
public record CreatePartUploadUrlsRequest(
        @NotEmpty List<@Positive Integer> partNumbers
) {
}
