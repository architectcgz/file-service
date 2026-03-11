package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.infrastructure.config.S3Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3StorageServicePublicUrlTest {

    @Test
    void getPublicUrlShouldPreferPublicEndpoint() {
        S3Properties properties = new S3Properties();
        properties.setEndpoint("http://file-service-minio:9000");
        properties.setPublicEndpoint("http://localhost:9000");
        properties.setAccessKey("fileservice");
        properties.setSecretKey("fileservice123");
        properties.setBucket("platform-files");
        properties.setPublicBucket("platform-files-public");
        properties.setPrivateBucket("platform-files-private");
        properties.setRegion("us-east-1");

        S3StorageService storageService = new S3StorageService(properties);

        String publicUrl = storageService.getPublicUrl("platform-files-public", "blog/2026/03/11/file.pdf");

        assertEquals("http://localhost:9000/platform-files-public/blog/2026/03/11/file.pdf", publicUrl);
    }
}
