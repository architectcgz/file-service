package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 预签名上传URL生成响应模型
 * 包含直接上传到S3的限时URL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    /**
     * 上传会话标识符。
     * 为兼容旧调用方，仍保留 fileId 字段名。
     */
    private String fileId;

    /**
     * 上传会话标识符
     */
    private String uploadSessionId;
    
    /**
     * 预签名上传URL
     */
    private String uploadUrl;

    /**
     * 上传方法
     */
    private String uploadMethod;

    /**
     * 上传时需要携带的请求头
     */
    private Map<String, String> uploadHeaders;
    
    /**
     * URL过期时间戳
     */
    private LocalDateTime expiresAt;
}
