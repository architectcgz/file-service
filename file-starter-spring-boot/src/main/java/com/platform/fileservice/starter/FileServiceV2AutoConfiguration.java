package com.platform.fileservice.starter;

import com.platform.fileservice.sdk.DefaultFileServiceV2Client;
import com.platform.fileservice.sdk.FileServiceV2Client;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Minimal auto-configuration that exposes a v2 client bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(FileServiceV2Properties.class)
public class FileServiceV2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FileServiceV2Client fileServiceV2Client(FileServiceV2Properties properties) {
        if (properties.getBaseUrl() == null) {
            throw new IllegalStateException("file.service.v2.base-url must be configured");
        }
        return new DefaultFileServiceV2Client(properties.getBaseUrl());
    }
}
