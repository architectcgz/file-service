package com.platform.fileservice.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 秒传检查请求模型
 * 用于检查具有相同哈希值的文件是否已存在
 */
@Data
@Builder
public class InstantUploadCheckRequest {
    /**
     * 文件哈希值（通常是SHA-256）
     */
    private String fileHash;
    
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
    
    /**
     * 文件访问级别
     */
    private AccessLevel accessLevel;
}
