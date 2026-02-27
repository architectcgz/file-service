package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 兼容存储配置属或
 * 支持 RustFS、MinIO、AWS S3、阿里云 OSS 或S3 兼容存储服务
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {
    
    /**
     * S3 endpoint URL (e.g., http://localhost:9000 for RustFS/MinIO)
     */
    private String endpoint;
    
    /**
     * Public endpoint URL for presigned URLs (e.g., http://localhost:9001)
     * If not set, will use endpoint
     */
    private String publicEndpoint;
    
    /**
     * S3 access key
     */
    private String accessKey;
    
    /**
     * S3 secret key
     */
    private String secretKey;
    
    /**
     * S3 bucket name (用于公开文件)
     */
    private String bucket;
    
    /**
     * 公开文件bucket (如果未配置，使用默认bucket)
     */
    private String publicBucket;
    
    /**
     * 私有文件bucket (如果未配置，使用默认bucket)
     */
    private String privateBucket;
    
    /**
     * S3 region (default: us-east-1)
     */
    private String region = "us-east-1";
    
    /**
     * Optional CDN domain for public file access
     */
    private String cdnDomain;
    
    /**
     * 是否使用 path-style access (RustFS/MinIO 需或
     * 默认为true，因为大多数自托或S3 兼容存储需要此模式
     */
    private boolean pathStyleAccess = true;
    
    /**
     * 获取公开文件bucket名称
     */
    public String getPublicBucket() {
        return publicBucket != null ? publicBucket : bucket;
    }
    
    /**
     * 获取私有文件bucket名称
     */
    public String getPrivateBucket() {
        return privateBucket != null ? privateBucket : bucket;
    }
    
    /**
     * 根据访问级别获取bucket名称
     */
    public String getBucketByAccessLevel(com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        if (accessLevel == com.architectcgz.file.domain.model.AccessLevel.PUBLIC) {
            return getPublicBucket();
        } else {
            return getPrivateBucket();
        }
    }
    
    /**
     * 获取用于生成预签名 URL 的 endpoint
     * 如果配置了 publicEndpoint，则使用它；否则使用 endpoint
     */
    public String getPresignEndpoint() {
        return publicEndpoint != null ? publicEndpoint : endpoint;
    }
}
