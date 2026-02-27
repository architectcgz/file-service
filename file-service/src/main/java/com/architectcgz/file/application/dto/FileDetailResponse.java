package com.architectcgz.file.application.dto;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件详情响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDetailResponse {
    
    /**
     * 文件记录ID
     */
    private String fileId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 内容类型 (MIME type)
     */
    private String contentType;
    
    /**
     * 文件哈希值
     */
    private String fileHash;
    
    /**
     * 哈希算法
     */
    private String hashAlgorithm;
    
    /**
     * 文件状态
     */
    private FileStatus status;
    
    /**
     * 访问级别
     */
    private AccessLevel accessLevel;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
