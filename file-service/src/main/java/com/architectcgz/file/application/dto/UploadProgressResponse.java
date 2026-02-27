package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传进度响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadProgressResponse {
    
    /**
     * 任务 ID
     */
    private String taskId;
    
    /**
     * 总分片数
     */
    private Integer totalParts;
    
    /**
     * 已完成分片数
     */
    private Integer completedParts;
    
    /**
     * 已上传字节数
     */
    private Long uploadedBytes;
    
    /**
     * 总字节数
     */
    private Long totalBytes;
    
    /**
     * 上传进度百分比(0-100)
     */
    private Integer percentage;
}
