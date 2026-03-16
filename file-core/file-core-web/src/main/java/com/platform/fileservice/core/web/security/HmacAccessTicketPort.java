package com.platform.fileservice.core.web.security;

import com.platform.fileservice.core.domain.model.AccessTicketGrant;
import com.platform.fileservice.core.ports.security.AccessTicketPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;

/**
 * 使用与 gateway 相同格式的 HMAC 票据，保证迁移期互通。
 */
public final class HmacAccessTicketPort implements AccessTicketPort {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TICKET_VERSION = "FGW1";

    private final String signingSecret;

    public HmacAccessTicketPort(String signingSecret) {
        this.signingSecret = Objects.requireNonNull(signingSecret, "signingSecret must not be null");
    }

    @Override
    public AccessTicketGrant issueTicket(AccessTicketGrant accessTicketGrant) {
        String payload = String.join("\n",
                TICKET_VERSION,
                accessTicketGrant.fileId(),
                accessTicketGrant.tenantId(),
                accessTicketGrant.subjectId() == null ? "" : accessTicketGrant.subjectId(),
                String.valueOf(accessTicketGrant.expiresAt().getEpochSecond()));
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload);
        return new AccessTicketGrant(
                accessTicketGrant.ticketId(),
                accessTicketGrant.fileId(),
                accessTicketGrant.tenantId(),
                accessTicketGrant.subjectId(),
                accessTicketGrant.issuedAt(),
                accessTicketGrant.expiresAt(),
                encodedPayload + "." + signature
        );
    }

    @Override
    public boolean revoke(String ticketId) {
        return false;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    signingSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("签发 access ticket 失败", ex);
        }
    }
}
