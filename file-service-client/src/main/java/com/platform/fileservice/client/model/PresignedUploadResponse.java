package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 预签名上传URL生成响应模型
 * 包含直接上传到S3的限时URL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    /**
     * 上传的文件标识符
     */
    private String fileId;
    
    /**
     * 预签名上传URL
     */
    private String uploadUrl;
    
    /**
     * URL过期时间戳
     */
    private LocalDateTime expiresAt;
}
