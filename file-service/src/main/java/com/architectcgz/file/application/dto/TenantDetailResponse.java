package com.architectcgz.file.application.dto;

import com.architectcgz.file.domain.model.TenantStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户详情响应
 */
@Data
public class TenantDetailResponse {
    private String tenantId;
    private String tenantName;
    private TenantStatus status;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private List<String> allowedFileTypes;
    private String contactEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 使用统计
    private Long usedStorageBytes;
    private Integer usedFileCount;
    private LocalDateTime lastUploadAt;
    private Double storageUsagePercent;
    private Double fileCountUsagePercent;
}
