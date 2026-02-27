package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 文件详情查询响应模型
 * 包含文件的完整元数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDetailResponse {
    /**
     * 唯一文件标识符
     */
    private String fileId;
    
    /**
     * 文件访问URL
     */
    private String url;
    
    /**
     * 原始文件名
     */
    private String originalName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
    
    /**
     * 文件访问级别（PUBLIC或PRIVATE）
     */
    private AccessLevel accessLevel;
    
    /**
     * 文件创建时间戳
     */
    private LocalDateTime createdAt;
    
    /**
     * 拥有该文件的租户ID（应用ID）
     */
    private String tenantId;
}
