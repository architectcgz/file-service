package com.platform.fileservice.contract.access.model;

import com.platform.fileservice.contract.common.FileServiceErrorCode;

import java.time.Instant;

/**
 * Per-file result for batch access ticket issuing.
 */
public record BatchAccessTicketItemView(
        String fileId,
        String ticket,
        String gatewayUrl,
        Instant expiresAt,
        FileServiceErrorCode errorCode,
        String message
) {
}
