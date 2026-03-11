package com.platform.filegateway.service;

import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.config.GatewayProperties;
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

    private final GatewayProperties gatewayProperties;

    public void verify(String fileId, String appId, String userId, Long expiresAt, String signature) {
        if (!StringUtils.hasText(appId) || expiresAt == null || !StringUtils.hasText(signature)) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "缺少签名访问参数");
        }

        long currentEpochSecond = Instant.now().getEpochSecond();
        if (expiresAt <= currentEpochSecond) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "签名已过期");
        }

        String expectedSignature = sign(fileId, appId, userId, expiresAt);
        boolean matched = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII)
        );
        if (!matched) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "签名无效");
        }
    }

    public String sign(String fileId, String appId, String userId, long expiresAt) {
        String payload = buildPayload(fileId, appId, userId, expiresAt);

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

    private String buildPayload(String fileId, String appId, String userId, long expiresAt) {
        return String.join("\n",
                "GET",
                "/api/v1/files/" + fileId + "/content",
                appId == null ? "" : appId,
                userId == null ? "" : userId,
                String.valueOf(expiresAt));
    }
}
