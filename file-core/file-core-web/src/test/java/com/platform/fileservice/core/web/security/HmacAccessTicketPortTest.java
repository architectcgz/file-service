package com.platform.fileservice.core.web.security;

import com.platform.fileservice.core.domain.model.AccessTicketGrant;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacAccessTicketPortTest {

    @Test
    void shouldProduceSignedTicket() {
        HmacAccessTicketPort port = new HmacAccessTicketPort("unit-test-secret");

        AccessTicketGrant issued = port.issueTicket(new AccessTicketGrant(
                "ticket-001",
                "file-001",
                "blog",
                "user-001",
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:05:00Z"),
                null
        ));

        assertNotNull(issued.serializedTicket());
        assertTrue(issued.serializedTicket().contains("."));
    }
}
