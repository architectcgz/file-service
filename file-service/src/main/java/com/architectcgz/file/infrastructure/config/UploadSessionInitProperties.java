package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 上传会话初始化互斥配置。
 */
@Data
@ConfigurationProperties(prefix = "file-service.upload-session-init")
public class UploadSessionInitProperties {

    /**
     * 是否启用基于 Redis 的初始化互斥。
     */
    private boolean enabled = true;

    /**
     * 获取同 hash 初始化锁的最长等待时间。
     */
    private Duration waitTimeout = Duration.ofSeconds(5);
}
