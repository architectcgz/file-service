package com.platform.fileservice.core.domain.model;

import java.time.Instant;

/**
 * Short-lived grant used by the download gateway.
 */
public record AccessTicketGrant(
        String ticketId,
        String fileId,
        String tenantId,
        String subjectId,
        Instant issuedAt,
        Instant expiresAt,
        String serializedTicket
) {

    public boolean isExpiredAt(Instant instant) {
        return expiresAt != null && !expiresAt.isAfter(instant);
    }
}
