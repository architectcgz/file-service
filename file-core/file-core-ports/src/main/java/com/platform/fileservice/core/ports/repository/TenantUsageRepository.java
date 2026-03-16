package com.platform.fileservice.core.ports.repository;

import com.platform.fileservice.core.domain.model.TenantUsage;

import java.util.Optional;

/**
 * Repository port for tenant usage aggregates.
 */
public interface TenantUsageRepository {

    Optional<TenantUsage> findByTenantId(String tenantId);
}
