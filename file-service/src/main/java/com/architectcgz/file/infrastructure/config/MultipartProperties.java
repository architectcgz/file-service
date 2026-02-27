package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分片上传配置属或
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.multipart")
public class MultipartProperties {
    
    /**
     * 是否启用分片上传
     */
    private boolean enabled = true;
    
    /**
     * 分片上传阈值（字节或
     * 超过此大小的文件将使用分片上或
     * 默认: 10MB
     */
    private long threshold = 10485760L;
    
    /**
     * 每个分片的大小（字节或
     * 默认: 5MB
     */
    private int chunkSize = 5242880;
    
    /**
     * 最大分片数或
     * 默认: 10000
     */
    private int maxParts = 10000;
    
    /**
     * 上传任务过期时间（小时）
     * 默认: 24小时
     */
    private int taskExpireHours = 24;
    
    /**
     * 过期任务清理 Cron 表达或
     * 默认: 每小时执行一或(0 0 * * * *)
     */
    private String cleanupCron = "0 0 * * * *";
}
