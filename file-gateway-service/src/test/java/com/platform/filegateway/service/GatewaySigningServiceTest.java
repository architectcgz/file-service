package com.platform.filegateway.service;

import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.config.GatewayProperties;
import com.platform.filegateway.domain.GatewayTicketClaims;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewaySigningServiceTest {

    private final GatewayProperties gatewayProperties = createProperties();
    private final GatewaySigningService gatewaySigningService = new GatewaySigningService(gatewayProperties);

    @Test
    void shouldVerifyValidSignature() {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String signature = gatewaySigningService.sign("file-001", "blog", "user-001", expiresAt);

        assertDoesNotThrow(() ->
                gatewaySigningService.verify("file-001", "blog", "user-001", expiresAt, signature));
    }

    @Test
    void shouldRejectExpiredSignature() {
        long expiresAt = Instant.now().minusSeconds(5).getEpochSecond();
        String signature = gatewaySigningService.sign("file-001", "blog", "user-001", expiresAt);

        GatewayException exception = assertThrows(GatewayException.class, () ->
                gatewaySigningService.verify("file-001", "blog", "user-001", expiresAt, signature));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void shouldRejectTamperedSignature() {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String signature = gatewaySigningService.sign("file-001", "blog", "user-001", expiresAt);

        GatewayException exception = assertThrows(GatewayException.class, () ->
                gatewaySigningService.verify("file-002", "blog", "user-001", expiresAt, signature));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void shouldVerifyValidTicket() {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String ticket = gatewaySigningService.signTicket("file-001", "blog", null, expiresAt);

        GatewayTicketClaims claims = gatewaySigningService.verifyTicket("file-001", ticket);

        assertEquals("file-001", claims.fileId());
        assertEquals("blog", claims.appId());
        assertNull(claims.userId());
        assertEquals(expiresAt, claims.expiresAt());
    }

    @Test
    void shouldRejectTicketForAnotherFile() {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String ticket = gatewaySigningService.signTicket("file-001", "blog", "user-001", expiresAt);

        GatewayException exception = assertThrows(GatewayException.class, () ->
                gatewaySigningService.verifyTicket("file-002", ticket));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    private GatewayProperties createProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getAuth().setSigningSecret("unit-test-secret");
        return properties;
    }
}
