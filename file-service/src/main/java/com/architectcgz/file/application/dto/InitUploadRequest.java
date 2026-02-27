package com.architectcgz.file.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 初始化分片上传请求
 */
@Data
public class InitUploadRequest {
    
    /**
     * 文件名
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;
    
    /**
     * 文件 hash (MD5 或 SHA256)，用于断点续传匹配
     */
    private String fileHash;
    
    /**
     * Content-Type
     */
    @NotBlank(message = "Content-Type不能为空")
    private String contentType;
}
