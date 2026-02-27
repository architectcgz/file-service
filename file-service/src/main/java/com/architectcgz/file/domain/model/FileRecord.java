package com.architectcgz.file.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件记录领域模型
 * 用于记录上传文件的元数据信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRecord {
    
    /**
     * 文件记录ID (UUIDv7)
     */
    private String id;
    
    /**
     * 应用ID (用于多租户隔离)
     */
    private String appId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 存储对象ID (关联 StorageObject)
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
     * 哈希算法 (MD5, SHA256)
     */
    private String hashAlgorithm;
    
    /**
     * 文件状态
     */
    private FileStatus status;
    
    /**
     * 访问级别 (PUBLIC, PRIVATE)
     * 默认为PUBLIC
     */
    private AccessLevel accessLevel;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 标记文件为已删除（软删除)
     */
    public void markAsDeleted() {
        this.status = FileStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 检查文件是否已删除
     */
    public boolean isDeleted() {
        return FileStatus.DELETED.equals(this.status);
    }
    
    /**
     * 检查文件是否已完成
     */
    public boolean isCompleted() {
        return FileStatus.COMPLETED.equals(this.status);
    }
    
    /**
     * 检查文件是否属于指定应用
     * 
     * @param appId 应用ID
     * @return 如果文件属于该应用返回 true，否则返回 false
     */
    public boolean belongsToApp(String appId) {
        return this.appId != null && this.appId.equals(appId);
    }
}
