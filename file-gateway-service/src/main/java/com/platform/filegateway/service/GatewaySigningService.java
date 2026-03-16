package com.platform.filegateway.service;

import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.config.GatewayProperties;
import com.platform.filegateway.domain.GatewayTicketClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GatewaySigningService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TICKET_VERSION = "FGW1";

    private final GatewayProperties gatewayProperties;

    public void verify(String fileId, String appId, String userId, Long expiresAt, String signature) {
        if (!StringUtils.hasText(appId) || expiresAt == null || !StringUtils.hasText(signature)) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "缺少签名访问参数");
        }

        long currentEpochSecond = Instant.now().getEpochSecond();
        if (expiresAt <= currentEpochSecond) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "签名已过期");
        }

        verifySignature(sign(buildLegacyPayload(fileId, appId, userId, expiresAt)), signature);
    }

    public String sign(String fileId, String appId, String userId, long expiresAt) {
        return sign(buildLegacyPayload(fileId, appId, userId, expiresAt));
    }

    public String signTicket(String fileId, String appId, String userId, long expiresAt) {
        String payload = buildTicketPayload(fileId, appId, userId, expiresAt);
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(payload);
    }

    public GatewayTicketClaims verifyTicket(String expectedFileId, String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "缺少访问票据");
        }

        String[] segments = ticket.split("\\.", 2);
        if (segments.length != 2 || !StringUtils.hasText(segments[0]) || !StringUtils.hasText(segments[1])) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据格式无效");
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据格式无效", ex);
        }

        verifySignature(sign(payload), segments[1]);

        GatewayTicketClaims claims = parseTicketPayload(payload);
        if (!expectedFileId.equals(claims.fileId())) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "签名无效");
        }

        if (!StringUtils.hasText(claims.appId())) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据缺少 appId");
        }

        long currentEpochSecond = Instant.now().getEpochSecond();
        if (claims.expiresAt() <= currentEpochSecond) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据已过期");
        }

        return claims;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    gatewayProperties.getAuth().getSigningSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new GatewayException(HttpStatus.INTERNAL_SERVER_ERROR, "生成签名失败", ex);
        }
    }

    private void verifySignature(String expectedSignature, String actualSignature) {
        boolean matched = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                actualSignature.getBytes(StandardCharsets.US_ASCII)
        );
        if (!matched) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "签名无效");
        }
    }

    private String buildLegacyPayload(String fileId, String appId, String userId, long expiresAt) {
        return String.join("\n",
                "GET",
                "/api/v1/files/" + fileId + "/content",
                appId == null ? "" : appId,
                userId == null ? "" : userId,
                String.valueOf(expiresAt));
    }

    private String buildTicketPayload(String fileId, String appId, String userId, long expiresAt) {
        return String.join("\n",
                TICKET_VERSION,
                fileId == null ? "" : fileId,
                appId == null ? "" : appId,
                userId == null ? "" : userId,
                String.valueOf(expiresAt));
    }

    private GatewayTicketClaims parseTicketPayload(String payload) {
        String[] parts = payload.split("\n", -1);
        if (parts.length != 5 || !TICKET_VERSION.equals(parts[0])) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据格式无效");
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[4]);
        } catch (NumberFormatException ex) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "访问票据格式无效", ex);
        }

        return new GatewayTicketClaims(
                parts[1],
                parts[2],
                StringUtils.hasText(parts[3]) ? parts[3] : null,
                expiresAt
        );
    }
}
