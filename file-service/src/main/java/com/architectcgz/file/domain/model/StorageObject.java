package com.architectcgz.file.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 存储对象领域模型
 * 用于文件去重，多个FileRecord 可以引用同一个StorageObject
 * 通过引用计数管理文件的生命周期
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageObject {
    
    /**
     * 存储对象ID (UUIDv7)
     */
    private String id;
    
    /**
     * 应用ID (用于多租户隔离)
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
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 内容类型 (MIME type)
     */
    private String contentType;
    
    /**
     * 引用计数
     * 记录有多少个 FileRecord 引用此存储对象
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
    
    /**
     * 增加引用计数
     */
    public void incrementReferenceCount() {
        if (this.referenceCount == null) {
            this.referenceCount = 1;
        } else {
            this.referenceCount++;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 减少引用计数
     */
    public void decrementReferenceCount() {
        if (this.referenceCount != null && this.referenceCount > 0) {
            this.referenceCount--;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 检查是否可以删除
     * 当引用计数为 0 时，可以删除实际的S3 对象
     */
    public boolean canBeDeleted() {
        return this.referenceCount != null && this.referenceCount == 0;
    }

    /**
     * 检查当前是否为最后一个引用（引用计数为 1）
     * 用于在执行 S3 删除前预判：若为最后一个引用，则递减后归零，需要删除 S3 对象
     */
    public boolean isLastReference() {
        return this.referenceCount != null && this.referenceCount == 1;
    }
}
