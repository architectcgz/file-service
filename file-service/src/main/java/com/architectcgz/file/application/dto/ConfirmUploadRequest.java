package com.architectcgz.file.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 确认上传完成请求
 * 客户端使用预签名 URL 上传完成后，调用此接口确认
 */
@Data
public class ConfirmUploadRequest {
    
    /**
     * 应用ID
     */
    @NotBlank(message = "应用ID不能为空")
    private String appId;
    
    /**
     * 存储路径
     * 从预签名响应中获取
     */
    @NotBlank(message = "存储路径不能为空")
    private String storagePath;
    
    /**
     * 文件 hash (MD5 或 SHA256)
     * 用于验证文件完整性
     */
    @NotBlank(message = "文件 hash 不能为空")
    private String fileHash;
    
    /**
     * 原始文件名
     */
    @NotBlank(message = "原始文件名不能为空")
    private String originalFilename;

    /**
     * 访问级别 (public, private)
     * 默认为 public
     */
    private String accessLevel = "public";
}
