package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件记录持久化对或
 * 对应数据库表 file_records
 */
@Data
public class FileRecordPO {
    
    /**
     * 文件记录ID (UUIDv7)
     */
    private String id;
    
    /**
     * 应用ID
     */
    private String appId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 存储对象ID (逻辑关联 storage_objects.id)
     */
    private String storageObjectId;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 存储路径
     */
    private String storagePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 内容类型 (MIME type)
     */
    private String contentType;
    
    /**
     * 文件哈希值(MD5 或SHA256)
     */
    private String fileHash;
    
    /**
     * 访问级别 (public, private)
     */
    private String accessLevel;
    
    /**
     * 文件状态(completed, deleted)
     */
    private String status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
