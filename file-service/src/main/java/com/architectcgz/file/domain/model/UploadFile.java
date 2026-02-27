package com.architectcgz.file.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 上传文件领域模型
 */
@Getter
@Builder
public class UploadFile {
    
    /**
     * 文件ID
     */
    private final String fileId;
    
    /**
     * 原始文件名
     */
    private final String originalFilename;
    
    /**
     * 存储路径
     */
    private final String storagePath;
    
    /**
     * 访问URL
     */
    private final String url;
    
    /**
     * 缩略图URL（仅图片)
     */
    private final String thumbnailUrl;
    
    /**
     * 文件大小（字节）
     */
    private final long size;
    
    /**
     * 文件类型
     */
    private final FileType fileType;
    
    /**
     * MIME类型
     */
    private final String contentType;
    
    /**
     * 上传者ID
     */
    private final String uploaderId;
    
    /**
     * 上传时间
     */
    private final LocalDateTime uploadedAt;
    
    /**
     * 文件类型枚举
     */
    public enum FileType {
        IMAGE,
        DOCUMENT,
        OTHER
    }
}
