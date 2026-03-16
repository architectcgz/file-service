package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.model.TenantPolicy;
import com.platform.fileservice.core.domain.model.TenantUsage;
import com.platform.fileservice.core.ports.repository.TenantPolicyRepository;
import com.platform.fileservice.core.ports.repository.TenantUsageRepository;

import java.util.Optional;

/**
 * Application service entry for tenant policy and usage queries.
 */
public final class TenantAdminAppService {

    private final TenantPolicyRepository tenantPolicyRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public TenantAdminAppService(TenantPolicyRepository tenantPolicyRepository,
                                 TenantUsageRepository tenantUsageRepository) {
        this.tenantPolicyRepository = tenantPolicyRepository;
        this.tenantUsageRepository = tenantUsageRepository;
    }

    public Optional<TenantPolicy> getTenantPolicy(String tenantId) {
        return tenantPolicyRepository.findByTenantId(tenantId);
    }

    public Optional<TenantUsage> getTenantUsage(String tenantId) {
        return tenantUsageRepository.findByTenantId(tenantId);
    }
}
