package com.platform.filegateway.domain;

/**
 * 自包含下载票据解码后的声明信息。
 */
public record GatewayTicketClaims(
        String fileId,
        String appId,
        String userId,
        long expiresAt
) {
}
