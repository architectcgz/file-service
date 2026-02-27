package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置属性
 * 
 * 管理文件服务的缓存相关配置参数，包括缓存开关和过期时间等
 */
@Data
@ConfigurationProperties(prefix = "file-service.cache")
public class CacheProperties {
    
    /**
     * 是否启用缓存
     * 默认为 true，可通过配置文件或环境变量关闭
     */
    private boolean enabled = true;
    
    /**
     * URL 缓存配置
     */
    private UrlCache url = new UrlCache();
    
    /**
     * URL 缓存配置类
     */
    @Data
    public static class UrlCache {
        /**
         * 缓存过期时间（秒）
         * 默认 3600 秒（1小时）
         */
        private long ttl = 3600;
    }
}
