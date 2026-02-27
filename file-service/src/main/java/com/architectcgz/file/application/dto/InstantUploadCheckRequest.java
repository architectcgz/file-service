package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒传检查请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantUploadCheckRequest {
    
    /**
     * 文件哈希值（MD5 或SHA256)
     */
    private String fileHash;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 内容类型（可选）
     */
    private String contentType;
}
