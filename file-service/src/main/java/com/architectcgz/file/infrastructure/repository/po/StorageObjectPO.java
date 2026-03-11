package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储对象持久化对或
 * 对应数据库表 storage_objects
 * 用于文件去重，多个file_records 可以引用同一个storage_object
 */
@Data
public class StorageObjectPO {
    
    /**
     * 存储对象ID (UUIDv7)
     */
    private String id;
    
    /**
     * 应用ID
     */
    private String appId;
    
    /**
     * 文件哈希值(MD5 或SHA256)
     */
    private String fileHash;
    
    /**
     * 哈希算法 (MD5, SHA256)
     */
    private String hashAlgorithm;
    
    /**
     * S3 存储路径
     */
    private String storagePath;

    /**
     * 存储桶名称
     */
    private String bucketName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 内容类型 (MIME type)
     */
    private String contentType;
    
    /**
     * 引用计数
     * 记录有多少个 file_records 引用此存储对象
     * 当引用计数为 0 时，可以删除实际的S3 对象
     */
    private Integer referenceCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
