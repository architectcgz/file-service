package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 租户配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {
    /**
     * 是否自动创建租户
     */
    private boolean autoCreate = true;

    /**
     * 默认配额配置
     */
    private DefaultQuota defaults = new DefaultQuota();

    @Data
    public static class DefaultQuota {
        /**
         * 默认最大存储空间（字节），默认 10GB
         */
        private Long maxStorageBytes = 10737418240L;

        /**
         * 默认最大文件数量，默认 10000
         */
        private Integer maxFileCount = 10000;

        /**
         * 默认单文件大小限制（字节），默认 100MB
         */
        private Long maxSingleFileSize = 104857600L;
    }

    // Convenience methods for backward compatibility
    public Long getDefaultMaxStorageBytes() {
        return defaults.getMaxStorageBytes();
    }

    public Integer getDefaultMaxFileCount() {
        return defaults.getMaxFileCount();
    }

    public Long getDefaultMaxSingleFileSize() {
        return defaults.getMaxSingleFileSize();
    }
}
