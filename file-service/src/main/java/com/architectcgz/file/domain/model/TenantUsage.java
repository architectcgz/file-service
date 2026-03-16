package com.architectcgz.file.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 租户使用统计领域模型
 */
@Data
@NoArgsConstructor
public class TenantUsage {
    private String tenantId;
    private Long usedStorageBytes;
    private Integer usedFileCount;
    private OffsetDateTime lastUploadAt;
    private OffsetDateTime updatedAt;

    public TenantUsage(String tenantId) {
        this.tenantId = tenantId;
        this.usedStorageBytes = 0L;
        this.usedFileCount = 0;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 增加使用量
     * @param fileSize 文件大小（字节）
     */
    public void incrementUsage(long fileSize) {
        this.usedStorageBytes += fileSize;
        this.usedFileCount += 1;
        this.lastUploadAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 减少使用量
     * @param fileSize 文件大小（字节）
     */
    public void decrementUsage(long fileSize) {
        this.usedStorageBytes = Math.max(0, this.usedStorageBytes - fileSize);
        this.usedFileCount = Math.max(0, this.usedFileCount - 1);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
