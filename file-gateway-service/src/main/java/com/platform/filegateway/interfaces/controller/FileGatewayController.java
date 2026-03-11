package com.platform.filegateway.interfaces.controller;

import com.platform.filegateway.domain.GatewayRedirectResponse;
import com.platform.filegateway.service.FileGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileGatewayController {

    private final FileGatewayService fileGatewayService;

    @GetMapping("/{fileId}/content")
    public ResponseEntity<Void> accessFileContent(@PathVariable String fileId,
                                                  @RequestHeader(value = "X-App-Id", required = false) String headerAppId,
                                                  @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                                  @RequestParam(value = "appId", required = false) String signedAppId,
                                                  @RequestParam(value = "userId", required = false) String signedUserId,
                                                  @RequestParam(value = "expiresAt", required = false) Long expiresAt,
                                                  @RequestParam(value = "signature", required = false) String signature) {
        GatewayRedirectResponse redirectResponse = fileGatewayService.resolveRedirect(
                fileId,
                headerAppId,
                headerUserId,
                signedAppId,
                signedUserId,
                expiresAt,
                signature
        );

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectResponse.location()));

        if (StringUtils.hasText(redirectResponse.cacheControl())) {
            builder.header(HttpHeaders.CACHE_CONTROL, redirectResponse.cacheControl());
        }
        return builder.build();
    }
}
