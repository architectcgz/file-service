package com.platform.fileservice.core.web.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 外部访问与票据签发相关配置。
 */
@Validated
@ConfigurationProperties(prefix = "file.core.access")
public class FileCoreAccessProperties {

    @NotBlank
    private String gatewayBaseUrl = "http://localhost:8090";

    private Duration ticketTtl = Duration.ofMinutes(5);

    @NotBlank
    private String signingSecret = "change-me-before-production";

    public String getGatewayBaseUrl() {
        return gatewayBaseUrl;
    }

    public void setGatewayBaseUrl(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }
}
