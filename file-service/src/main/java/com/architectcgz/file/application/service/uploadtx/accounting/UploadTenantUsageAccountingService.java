package com.architectcgz.file.application.service.uploadtx.accounting;

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

    public void incrementUsage(String appId, long fileSize) {
        tenantUsageRepository.incrementUsage(appId, fileSize);
        log.debug("Tenant usage incremented: appId={}, delta={}", appId, fileSize);
    }
}
