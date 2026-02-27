package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.TenantUsage;

import java.util.Optional;

/**
 * 租户使用统计仓储接口
 */
public interface TenantUsageRepository {
    /**
     * 根据租户ID查找使用统计
     */
    Optional<TenantUsage> findById(String tenantId);

    /**
     * 保存使用统计
     */
    TenantUsage save(TenantUsage usage);

    /**
     * 原子性增加使用量
     * @param tenantId 租户ID
     * @param fileSize 文件大小（字节）
     */
    void incrementUsage(String tenantId, long fileSize);

    /**
     * 原子性减少使用量
     * @param tenantId 租户ID
     * @param fileSize 文件大小（字节）
     */
    void decrementUsage(String tenantId, long fileSize);
}
