package com.platform.fileservice.core.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Upload session runtime settings exposed by the V1 facade.
 */
@ConfigurationProperties(prefix = "file.core.upload")
public class FileCoreUploadProperties {

    private Duration sessionTtl = Duration.ofHours(24);
    private Duration partUrlTtl = Duration.ofMinutes(15);
    private int chunkSizeBytes = 5 * 1024 * 1024;
    private int maxParts = 10_000;
    private long autoPresignedSingleMaxSizeBytes = 10L * 1024 * 1024;

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getPartUrlTtl() {
        return partUrlTtl;
    }

    public void setPartUrlTtl(Duration partUrlTtl) {
        this.partUrlTtl = partUrlTtl;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public int getMaxParts() {
        return maxParts;
    }

    public void setMaxParts(int maxParts) {
        this.maxParts = maxParts;
    }

    public long getAutoPresignedSingleMaxSizeBytes() {
        return autoPresignedSingleMaxSizeBytes;
    }

    public void setAutoPresignedSingleMaxSizeBytes(long autoPresignedSingleMaxSizeBytes) {
        this.autoPresignedSingleMaxSizeBytes = autoPresignedSingleMaxSizeBytes;
    }
}
