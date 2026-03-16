package com.platform.fileservice.sdk;

import java.net.URI;
import java.util.Objects;

/**
 * Default immutable client descriptor for v2 integration.
 */
public final class DefaultFileServiceV2Client implements FileServiceV2Client {

    private final URI baseUrl;

    public DefaultFileServiceV2Client(URI baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    @Override
    public URI getBaseUrl() {
        return baseUrl;
    }
}
