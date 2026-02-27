package com.architectcgz.file.interfaces.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 上传结果DTO
 */
@Data
@Builder
public class UploadResult {
    
    /**
     * 文件ID
     */
    private String fileId;
    
    /**
     * 访问URL
     */
    private String url;
    
    /**
     * 缩略图URL（仅图片)
     */
    private String thumbnailUrl;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 文件大小（字节）
     */
    private long size;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 内容类型
     */
    private String contentType;
}
