package com.architectcgz.file.application.service.tenantmanagement.persistence;

import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户持久化服务。
 */
@Service
@RequiredArgsConstructor
public class TenantPersistenceService {

    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public Tenant saveTenant(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    public void saveTenantUsage(TenantUsage tenantUsage) {
        tenantUsageRepository.save(tenantUsage);
    }
}
