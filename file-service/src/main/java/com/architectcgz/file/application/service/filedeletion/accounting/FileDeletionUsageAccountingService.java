package com.architectcgz.file.application.service.filedeletion.accounting;

import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件删除相关的租户用量记账服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionUsageAccountingService {

    private final TenantUsageRepository tenantUsageRepository;

    public void decrementUsage(String appId, Long fileSize) {
        tenantUsageRepository.decrementUsage(appId, fileSize);
        log.debug("租户用量已递减: appId={}, size={}", appId, fileSize);
    }
}
