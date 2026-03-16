package com.architectcgz.file.domain.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 租户领域模型
 */
@Data
public class Tenant {
    private String tenantId;
    private String tenantName;
    private TenantStatus status;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private List<String> allowedFileTypes;
    private String contactEmail;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 停用租户
     */
    public void suspend() {
        if (this.status == TenantStatus.DELETED) {
            throw new IllegalStateException("Cannot suspend deleted tenant: " + tenantId);
        }
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 激活租户
     */
    public void activate() {
        if (this.status == TenantStatus.DELETED) {
            throw new IllegalStateException("Cannot activate deleted tenant: " + tenantId);
        }
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 标记为已删除（软删除）
     */
    public void markDeleted() {
        this.status = TenantStatus.DELETED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
