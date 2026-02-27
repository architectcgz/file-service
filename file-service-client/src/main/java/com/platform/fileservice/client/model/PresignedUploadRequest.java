package com.platform.fileservice.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 预签名上传URL生成请求模型
 * 用于获取直接上传到S3的限时URL
 */
@Data
@Builder
public class PresignedUploadRequest {
    /**
     * 原始文件名
     */
    private String filename;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件访问级别（PUBLIC或PRIVATE）
     */
    private AccessLevel accessLevel;
    
    /**
     * URL过期时间（秒）（可选）
     */
    private Integer expirationSeconds;
}
