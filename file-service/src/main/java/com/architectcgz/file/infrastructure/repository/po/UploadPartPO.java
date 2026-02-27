package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传分片持久化对或
 * 对应数据库表 upload_parts
 */
@Data
public class UploadPartPO {
    
    /**
     * 分片ID (UUIDv7)
     */
    private String id;
    
    /**
     * 任务ID (逻辑关联 upload_tasks.id)
     */
    private String taskId;
    
    /**
     * 分片编号 (或开或
     */
    private Integer partNumber;
    
    /**
     * S3 返回的ETag
     */
    private String etag;
    
    /**
     * 分片大小（字节）
     */
    private Long size;
    
    /**
     * 上传时间
     */
    private LocalDateTime uploadedAt;
}
