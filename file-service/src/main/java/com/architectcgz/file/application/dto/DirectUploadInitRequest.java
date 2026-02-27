package com.architectcgz.file.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 直传上传初始化请求
 */
@Data
public class DirectUploadInitRequest {
    
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
     * 文件内容类型
     */
    @NotBlank(message = "文件类型不能为空")
    private String contentType;
    
    /**
     * 文件哈希值（可选，用于断点续传）
     */
    private String fileHash;
}
