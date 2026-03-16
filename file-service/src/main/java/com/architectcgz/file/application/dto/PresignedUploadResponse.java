package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 预签名上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    
    /**
     * 预签名上传URL
     * 客户端使用此 URL 直接上传文件名S3
     */
    private String presignedUrl;
    
    /**
     * 存储路径
     * 用于确认上传完成时传递
     */
    private String storagePath;
    
    /**
     * URL 过期时间
     */
    private OffsetDateTime expiresAt;
    
    /**
     * HTTP 方法 (通常是PUT)
     */
    private String method;
    
    /**
     * 需要设置的请求头
     * 例如: {"Content-Type": "image/jpeg"}
     */
    private Map<String, String> headers;
}
