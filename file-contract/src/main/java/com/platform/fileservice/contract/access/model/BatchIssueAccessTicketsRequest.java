package com.platform.fileservice.contract.access.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for issuing access tickets for multiple files.
 */
public record BatchIssueAccessTicketsRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotBlank String> fileIds
) {
}
