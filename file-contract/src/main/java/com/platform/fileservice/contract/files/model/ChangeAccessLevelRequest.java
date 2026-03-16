package com.platform.fileservice.contract.files.model;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for changing the access level of a file.
 */
public record ChangeAccessLevelRequest(@NotNull AccessLevelView accessLevel) {
}
