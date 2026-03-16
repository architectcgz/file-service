package com.architectcgz.file.application.dto;

import com.architectcgz.file.domain.model.AccessLevel;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 文件查询对象
 * 用于文件列表查询的过滤和分页参数
 */
@Data
public class FileQuery {
    
    /**
     * 租户 ID
     */
    private String tenantId;
    
    /**
     * 用户 ID
     */
    private String userId;
    
    /**
     * 文件类型（content type）
     */
    private String contentType;
    
    /**
     * 访问级别
     */
    private AccessLevel accessLevel;
    
    /**
     * 开始时间（上传时间范围）
     */
    private OffsetDateTime startTime;
    
    /**
     * 结束时间（上传时间范围）
     */
    private OffsetDateTime endTime;
    
    /**
     * 最小文件大小（字节）
     */
    private Long minSize;
    
    /**
     * 最大文件大小（字节）
     */
    private Long maxSize;
    
    /**
     * 页码（从 0 开始）
     */
    private int page = 0;
    
    /**
     * 每页大小
     */
    private int size = 20;
    
    /**
     * 排序字段
     */
    private String sortBy = "createdAt";
    
    /**
     * 排序方向（asc 或 desc）
     */
    private String sortOrder = "desc";
}
