package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件访问控制配置属性
 * 用于配置公开文件和私有文件的访问策略
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.access")
public class AccessProperties {
    
    /**
     * 私有文件临时 URL 过期时间（秒传
     * 当用户请求访问私有文件时，系统会生成一个带有过期时间的预签名URL
     * 默认: 3600或(1小时)
     */
    private int privateUrlExpireSeconds = 3600;
    
    /**
     * 预签名上传URL 过期时间（秒传
     * 用于客户端直传到 S3 的预签名 URL 有效或
     * 默认: 900或(15分钟)
     */
    private int presignedUrlExpireSeconds = 900;
}
