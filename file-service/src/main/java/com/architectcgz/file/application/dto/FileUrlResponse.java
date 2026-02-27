package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件 URL 响应 DTO
 * 返回文件访问 URL 及相关信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUrlResponse {
    
    /**
     * 文件访问 URL
     */
    private String url;
    
    /**
     * 是否为永久URL
     * true: 公开文件的永久URL
     * false: 私有文件的临时预签名 URL
     */
    private Boolean permanent;
    
    /**
     * URL 过期时间
     * 仅当 permanent = false 时有效
     */
    private LocalDateTime expiresAt;
}
