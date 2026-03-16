package com.platform.fileservice.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Externalized configuration for the v2 SDK starter.
 */
@ConfigurationProperties(prefix = "file.service.v2")
public class FileServiceV2Properties {

    private URI baseUrl;

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }
}
