package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传任务持久化对或
 * 对应数据库表 upload_tasks
 */
@Data
public class UploadTaskPO {
    
    /**
     * 任务ID (UUIDv7)
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
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件哈希值
     */
    private String fileHash;

    /**
     * 文件 MIME 类型
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
     * 任务状态(uploading, completed, aborted, expired)
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
    
    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;
}
