package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 分片上传初始化响应模型
 * 包含上传分片所需的信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartInitResponse {
    /**
     * 分片上传任务标识符
     */
    private String taskId;
    
    /**
     * S3上传ID
     */
    private String uploadId;
    
    /**
     * 预期的分片总数
     */
    private Integer totalChunks;
    
    /**
     * 每个分片的大小（字节）（最后一个分片可能不同）
     */
    private Long chunkSize;
}
