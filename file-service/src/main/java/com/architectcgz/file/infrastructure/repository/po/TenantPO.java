package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户持久化对象
 */
@Data
public class TenantPO {
    private String tenantId;
    private String tenantName;
    private String status;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private String[] allowedFileTypes;
    private String contactEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
