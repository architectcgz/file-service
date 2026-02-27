package com.platform.fileservice.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 分片上传初始化请求模型
 * 用于启动大文件的分片上传会话
 */
@Data
@Builder
public class MultipartInitRequest {
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
    
    /**
     * 文件总大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件访问级别（PUBLIC或PRIVATE）
     */
    private AccessLevel accessLevel;
    
    /**
     * 每个分片的大小（字节）
     */
    private Long chunkSize;
}
