package com.architectcgz.file.domain.service;

import com.architectcgz.file.common.exception.FileTooLargeException;
import com.architectcgz.file.common.exception.QuotaExceededException;
import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.common.exception.TenantSuspendedException;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.config.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 租户领域服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDomainService {
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final TenantProperties tenantProperties;

    /**
     * 检查租户配额
     * @param tenantId 租户ID
     * @param fileSize 文件大小
     * @throws TenantNotFoundException 租户不存在
     * @throws TenantSuspendedException 租户已停用
     * @throws QuotaExceededException 配额超限
     * @throws FileTooLargeException 文件过大
     */
    public void checkQuota(String tenantId, long fileSize) {
        // 获取或创建租户
        Tenant tenant = getOrCreateTenant(tenantId);

        // 检查租户状态
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }

        // 获取使用统计
        TenantUsage usage = tenantUsageRepository.findById(tenantId)
                .orElse(new TenantUsage(tenantId));

        // 检查单文件大小
        if (fileSize > tenant.getMaxSingleFileSize()) {
            throw new FileTooLargeException(fileSize, tenant.getMaxSingleFileSize());
        }

        // 检查存储空间
        long newStorageUsage = usage.getUsedStorageBytes() + fileSize;
        if (newStorageUsage > tenant.getMaxStorageBytes()) {
            throw new QuotaExceededException(
                "Storage",
                newStorageUsage,
                tenant.getMaxStorageBytes()
            );
        }

        // 检查文件数量
        int newFileCount = usage.getUsedFileCount() + 1;
        if (newFileCount > tenant.getMaxFileCount()) {
            throw new QuotaExceededException(
                "File count",
                newFileCount,
                tenant.getMaxFileCount()
            );
        }
    }

    /**
     * 轻量上传预检查。
     *
     * 仅校验租户存在、租户状态和单文件大小，不读取 tenant_usage。
     * 真正的存储空间 / 文件数占用在事务内通过原子更新完成。
     */
    public Tenant validateUploadPrerequisites(String tenantId, long fileSize) {
        Tenant tenant = getOrCreateTenant(tenantId);

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }

        if (fileSize > tenant.getMaxSingleFileSize()) {
            throw new FileTooLargeException(fileSize, tenant.getMaxSingleFileSize());
        }

        return tenant;
    }

    /**
     * 获取或创建租户
     * @param tenantId 租户ID
     * @return 租户对象
     * @throws TenantNotFoundException 当自动创建禁用且租户不存在时
     */
    public Tenant getOrCreateTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseGet(() -> {
                    if (!tenantProperties.isAutoCreate()) {
                        throw new TenantNotFoundException(tenantId);
                    }
                    log.info("Auto-creating tenant: {}", tenantId);
                    return createDefaultTenant(tenantId);
                });
    }

    /**
     * 创建默认租户
     * @param tenantId 租户ID
     * @return 创建的租户对象
     */
    private Tenant createDefaultTenant(String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxStorageBytes(tenantProperties.getDefaultMaxStorageBytes());
        tenant.setMaxFileCount(tenantProperties.getDefaultMaxFileCount());
        tenant.setMaxSingleFileSize(tenantProperties.getDefaultMaxSingleFileSize());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());

        Tenant savedTenant = saveTenantIfAbsent(tenant);
        ensureTenantUsageExists(tenantId);

        return savedTenant;
    }

    private Tenant saveTenantIfAbsent(Tenant tenant) {
        try {
            return tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException ex) {
            log.info("Tenant auto-create raced with another request, reusing existing tenant: {}", tenant.getTenantId());
            return tenantRepository.findById(tenant.getTenantId()).orElse(tenant);
        }
    }

    private void ensureTenantUsageExists(String tenantId) {
        try {
            tenantUsageRepository.save(new TenantUsage(tenantId));
        } catch (DataIntegrityViolationException ex) {
            log.info("Tenant usage auto-create raced with another request, reusing existing usage row: {}", tenantId);
        }
    }
}
