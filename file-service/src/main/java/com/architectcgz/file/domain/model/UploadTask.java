package com.architectcgz.file.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 上传任务领域模型
 * 用于大文件分片上传和断点续传
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTask {
    
    /**
     * 任务ID (UUIDv7 - 时间有序)
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
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件哈希值（用于断点续传匹配)
     */
    private String fileHash;

    /**
     * 文件 MIME 类型（如 image/png、application/pdf）
     */
    private String contentType;
    
    /**
     * 存储路径
     */
    private String storagePath;
    
    /**
     * S3 Multipart Upload ID
     */
    private String uploadId;
    
    /**
     * 总分片数
     */
    private Integer totalParts;
    
    /**
     * 分片大小（字节）
     */
    private Integer chunkSize;
    
    /**
     * 任务状态
     */
    private UploadTaskStatus status;
    
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
    
    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
    
    /**
     * 过期时间
     */
    private OffsetDateTime expiresAt;
    
    /**
     * 检查任务是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }
    
    /**
     * 检查任务是否可以续传
     */
    public boolean canResume() {
        return status == UploadTaskStatus.UPLOADING && !isExpired();
    }
    
    /**
     * 标记任务为完成
     */
    public void markCompleted() {
        this.status = UploadTaskStatus.COMPLETED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    
    /**
     * 标记任务为中止
     */
    public void markAborted() {
        this.status = UploadTaskStatus.ABORTED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    
    /**
     * 标记任务为过期
     */
    public void markExpired() {
        this.status = UploadTaskStatus.EXPIRED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
