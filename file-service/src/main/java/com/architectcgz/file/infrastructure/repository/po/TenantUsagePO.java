package com.architectcgz.file.infrastructure.repository.po;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 租户使用统计持久化对象
 */
@Data
public class TenantUsagePO {
    private String tenantId;
    private Long usedStorageBytes;
    private Integer usedFileCount;
    private OffsetDateTime lastUploadAt;
    private OffsetDateTime updatedAt;
}
