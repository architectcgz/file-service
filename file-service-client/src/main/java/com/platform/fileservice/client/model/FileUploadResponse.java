package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 文件上传响应模型
 * 包含已上传文件的元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    /**
     * 唯一文件标识符
     */
    private String fileId;
    
    /**
     * 文件访问URL
     */
    private String url;
    
    /**
     * 原始文件名
     */
    private String originalName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
    
    /**
     * 文件访问级别
     */
    private AccessLevel accessLevel;
}
