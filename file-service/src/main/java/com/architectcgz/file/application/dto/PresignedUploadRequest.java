package com.architectcgz.file.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 预签名上传请求
 */
@Data
public class PresignedUploadRequest {
    
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
     * 文件类型 (MIME type)
     */
    @NotBlank(message = "文件类型不能为空")
    private String contentType;
    
    /**
     * 文件 hash (MD5 或 SHA256)
     * 用于秒传和去重
     */
    @NotBlank(message = "文件 hash 不能为空")
    private String fileHash;
    
    /**
     * 访问级别 (public, private)
     * 默认为 public
     */
    private String accessLevel = "public";
}
