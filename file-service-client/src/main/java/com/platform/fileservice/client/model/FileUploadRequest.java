package com.platform.fileservice.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传请求模型
 * 用于图片和普通文件上传操作
 */
@Data
@Builder
public class FileUploadRequest {
    /**
     * 文件访问级别（PUBLIC或PRIVATE）
     */
    private AccessLevel accessLevel;
    
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
}
