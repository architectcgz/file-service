package com.architectcgz.file.application.service.uploadtx.accounting;

import com.architectcgz.file.common.exception.QuotaExceededException;
import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.common.exception.TenantSuspendedException;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上传租户用量记账服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTenantUsageAccountingService {

    private final TenantUsageRepository tenantUsageRepository;
    private final TenantRepository tenantRepository;

    public void incrementUsage(String appId, long fileSize) {
        if (tenantUsageRepository.incrementUsageIfWithinQuota(appId, fileSize)) {
            log.debug("Tenant usage incremented atomically: appId={}, delta={}", appId, fileSize);
            return;
        }

        Tenant tenant = tenantRepository.findById(appId)
                .orElseThrow(() -> new TenantNotFoundException(appId));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(appId);
        }

        TenantUsage usage = tenantUsageRepository.findById(appId)
                .orElse(new TenantUsage(appId));

        long newStorageUsage = usage.getUsedStorageBytes() + fileSize;
        if (newStorageUsage > tenant.getMaxStorageBytes()) {
            throw new QuotaExceededException("Storage", newStorageUsage, tenant.getMaxStorageBytes());
        }

        int newFileCount = usage.getUsedFileCount() + 1;
        if (newFileCount > tenant.getMaxFileCount()) {
            throw new QuotaExceededException("File count", newFileCount, tenant.getMaxFileCount());
        }

        throw new QuotaExceededException("Upload", usage.getUsedFileCount(), tenant.getMaxFileCount());
    }
}
