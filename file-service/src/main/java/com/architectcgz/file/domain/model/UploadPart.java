package com.architectcgz.file.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 上传分片领域模型
 * 记录分片上传的单个分片信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPart {
    
    /**
     * 分片ID (UUIDv7 - 时间有序)
     */
    private String id;
    
    /**
     * 上传任务ID
     */
    private String taskId;
    
    /**
     * 分片编号（从1开始）
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
    
    /**
     * 验证分片编号是否有效
     */
    public boolean isValidPartNumber(int totalParts) {
        return partNumber != null && partNumber > 0 && partNumber <= totalParts;
    }
}
