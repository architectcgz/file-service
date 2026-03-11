package com.platform.filegateway.service;

import com.platform.filegateway.client.UpstreamRedirectClient;
import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.config.GatewayProperties;
import com.platform.filegateway.domain.GatewayAccessIdentity;
import com.platform.filegateway.domain.GatewayRedirectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileGatewayServiceTest {

    @Mock
    private UpstreamRedirectClient upstreamRedirectClient;

    private GatewaySigningService gatewaySigningService;

    private FileGatewayService fileGatewayService;

    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.getAuth().setSigningSecret("unit-test-secret");
        gatewayProperties.getAuth().setAllowHeaderIdentity(true);
        gatewaySigningService = new GatewaySigningService(gatewayProperties);
        fileGatewayService = new FileGatewayService(gatewayProperties, gatewaySigningService, upstreamRedirectClient);
    }

    @Test
    void shouldResolveRedirectFromSignedQuery() {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String signature = gatewaySigningService.sign("file-001", "blog", "user-001", expiresAt);
        GatewayRedirectResponse expected = new GatewayRedirectResponse("https://cdn.example.com/file-001", "no-store");
        when(upstreamRedirectClient.resolveContentRedirect("file-001",
                new GatewayAccessIdentity("blog", "user-001"))).thenReturn(expected);

        GatewayRedirectResponse actual = fileGatewayService.resolveRedirect(
                "file-001",
                null,
                null,
                "blog",
                "user-001",
                expiresAt,
                signature
        );

        assertSame(expected, actual);
        verify(upstreamRedirectClient).resolveContentRedirect("file-001",
                new GatewayAccessIdentity("blog", "user-001"));
    }

    @Test
    void shouldFallbackToHeaderIdentity() {
        GatewayRedirectResponse expected = new GatewayRedirectResponse("https://cdn.example.com/file-001", null);
        when(upstreamRedirectClient.resolveContentRedirect("file-001",
                new GatewayAccessIdentity("blog", "user-001"))).thenReturn(expected);

        GatewayRedirectResponse actual = fileGatewayService.resolveRedirect(
                "file-001",
                "blog",
                "user-001",
                null,
                null,
                null,
                null
        );

        assertSame(expected, actual);
        verify(upstreamRedirectClient).resolveContentRedirect("file-001",
                new GatewayAccessIdentity("blog", "user-001"));
    }

    @Test
    void shouldRejectMissingIdentity() {
        GatewayException exception = assertThrows(GatewayException.class, () ->
                fileGatewayService.resolveRedirect("file-001", null, null, null, null, null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void shouldRejectHeaderIdentityWhenDisabled() {
        gatewayProperties.getAuth().setAllowHeaderIdentity(false);

        GatewayException exception = assertThrows(GatewayException.class, () ->
                fileGatewayService.resolveRedirect("file-001", "blog", "user-001", null, null, null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
