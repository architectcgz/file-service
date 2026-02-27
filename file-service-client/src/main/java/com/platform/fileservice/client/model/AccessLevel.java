package com.platform.fileservice.client.model;

/**
 * 文件访问级别枚举
 * 定义文件是公开访问还是需要认证
 */
public enum AccessLevel {
    /**
     * 公开文件无需认证即可访问
     * 通常通过CDN提供以获得更好的性能
     */
    PUBLIC,
    
    /**
     * 私有文件需要认证才能访问
     * 访问URL是带有过期时间的预签名URL
     */
    PRIVATE
}
