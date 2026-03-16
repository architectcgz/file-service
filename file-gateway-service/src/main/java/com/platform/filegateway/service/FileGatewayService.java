package com.platform.filegateway.service;

import com.platform.filegateway.client.UpstreamRedirectClient;
import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.config.GatewayProperties;
import com.platform.filegateway.domain.GatewayAccessIdentity;
import com.platform.filegateway.domain.GatewayRedirectResponse;
import com.platform.filegateway.domain.GatewayTicketClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FileGatewayService {

    private final GatewayProperties gatewayProperties;
    private final GatewaySigningService gatewaySigningService;
    private final UpstreamRedirectClient upstreamRedirectClient;

    public GatewayRedirectResponse resolveRedirect(String fileId,
                                                   String ticket,
                                                   String headerAppId,
                                                   String headerUserId,
                                                   String signedAppId,
                                                   String signedUserId,
                                                   Long expiresAt,
                                                   String signature) {
        GatewayAccessIdentity identity = resolveIdentity(
                fileId,
                ticket,
                headerAppId,
                headerUserId,
                signedAppId,
                signedUserId,
                expiresAt,
                signature
        );

        return upstreamRedirectClient.resolveContentRedirect(fileId, identity);
    }

    private GatewayAccessIdentity resolveIdentity(String fileId,
                                                  String ticket,
                                                  String headerAppId,
                                                  String headerUserId,
                                                  String signedAppId,
                                                  String signedUserId,
                                                  Long expiresAt,
                                                  String signature) {
        if (StringUtils.hasText(ticket)) {
            GatewayTicketClaims claims = gatewaySigningService.verifyTicket(fileId, ticket);
            return new GatewayAccessIdentity(claims.appId(), normalize(claims.userId()));
        }

        if (containsSignedAccessParams(signedAppId, signedUserId, expiresAt, signature)) {
            gatewaySigningService.verify(fileId, signedAppId, normalize(signedUserId), expiresAt, signature);
            return new GatewayAccessIdentity(signedAppId, normalize(signedUserId));
        }

        if (gatewayProperties.getAuth().isAllowHeaderIdentity() && StringUtils.hasText(headerAppId)) {
            return new GatewayAccessIdentity(headerAppId, normalize(headerUserId));
        }

        throw new GatewayException(HttpStatus.UNAUTHORIZED,
                "缺少访问身份，请提供 ticket、签名参数或受信任的请求头");
    }

    private boolean containsSignedAccessParams(String signedAppId,
                                               String signedUserId,
                                               Long expiresAt,
                                               String signature) {
        return StringUtils.hasText(signedAppId)
                || StringUtils.hasText(signedUserId)
                || expiresAt != null
                || StringUtils.hasText(signature);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
