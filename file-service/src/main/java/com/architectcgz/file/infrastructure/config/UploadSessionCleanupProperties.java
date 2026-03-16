package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上传会话清理任务的分布式互斥配置。
 */
@Data
@ConfigurationProperties(prefix = "file-service.cleanup.upload-sessions")
public class UploadSessionCleanupProperties {

    /**
     * 清理任务分布式锁的超时时间（秒）。
     */
    private long lockTimeoutSeconds = 1800;
}
