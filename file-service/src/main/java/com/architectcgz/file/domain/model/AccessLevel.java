package com.architectcgz.file.domain.model;

/**
 * 文件访问级别枚举
 * 定义文件的访问权限
 */
public enum AccessLevel {
    
    /**
     * 公开访问
     * 任何人都可以通过永久 URL 访问文件
     */
    PUBLIC,
    
    /**
     * 私有访问
     * 只有文件所有者可以访问，通过临时预签名URL
     */
    PRIVATE
}
