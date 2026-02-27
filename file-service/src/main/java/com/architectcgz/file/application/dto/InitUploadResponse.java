package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 初始化分片上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {
    
    /**
     * 任务 ID
     */
    private String taskId;
    
    /**
     * S3 Multipart Upload ID
     */
    private String uploadId;
    
    /**
     * 分片大小（字节）
     */
    private Integer chunkSize;
    
    /**
     * 总分片数
     */
    private Integer totalParts;
    
    /**
     * 已完成的分片号列表（断点续传时返回）
     */
    private List<Integer> completedParts;
}
