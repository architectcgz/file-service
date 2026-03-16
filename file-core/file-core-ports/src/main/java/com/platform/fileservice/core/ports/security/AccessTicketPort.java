package com.platform.fileservice.core.ports.security;

import com.platform.fileservice.core.domain.model.AccessTicketGrant;

/**
 * Port for issuing and revoking download access tickets.
 */
public interface AccessTicketPort {

    AccessTicketGrant issueTicket(AccessTicketGrant accessTicketGrant);

    boolean revoke(String ticketId);
}
