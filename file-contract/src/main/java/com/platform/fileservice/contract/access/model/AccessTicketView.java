package com.platform.fileservice.contract.access.model;

import java.time.Instant;

/**
 * Download ticket view returned by file access APIs.
 */
public record AccessTicketView(
        String ticket,
        String gatewayUrl,
        Instant expiresAt
) {
}
