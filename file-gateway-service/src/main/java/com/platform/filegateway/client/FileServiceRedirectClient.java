package com.platform.filegateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.filegateway.common.exception.GatewayException;
import com.platform.filegateway.domain.GatewayAccessIdentity;
import com.platform.filegateway.domain.GatewayRedirectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceRedirectClient implements UpstreamRedirectClient {

    private final RestClient fileServiceRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public GatewayRedirectResponse resolveContentRedirect(String fileId, GatewayAccessIdentity identity) {
        try {
            return fileServiceRestClient.get()
                    .uri("/api/v1/files/{fileId}/content", fileId)
                    .headers(headers -> {
                        headers.set("X-App-Id", identity.appId());
                        if (StringUtils.hasText(identity.userId())) {
                            headers.set("X-User-Id", identity.userId());
                        }
                    })
                    .exchange((request, response) -> mapResponse(fileId, response));
        } catch (ResourceAccessException ex) {
            throw new GatewayException(HttpStatus.BAD_GATEWAY, "file-service 不可用", ex);
        }
    }

    private GatewayRedirectResponse mapResponse(String fileId, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is3xxRedirection()) {
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (!StringUtils.hasText(location)) {
                throw new GatewayException(HttpStatus.BAD_GATEWAY,
                        "file-service 返回的重定向缺少 Location");
            }

            log.debug("Resolved redirect from file-service: fileId={}, location={}", fileId, location);
            return new GatewayRedirectResponse(location, response.getHeaders().getCacheControl());
        }

        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        String message = extractMessage(responseBody);
        if (!StringUtils.hasText(message)) {
            message = "file-service 返回异常: HTTP " + status.value();
        }

        throw new GatewayException(status, message);
    }

    private String extractMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode messageNode = rootNode.path("message");
            return messageNode.isMissingNode() || messageNode.isNull() ? null : messageNode.asText();
        } catch (Exception ex) {
            log.debug("Failed to parse upstream error body", ex);
            return null;
        }
    }
}
