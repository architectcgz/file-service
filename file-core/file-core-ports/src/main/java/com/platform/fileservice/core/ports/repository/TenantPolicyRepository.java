package com.platform.fileservice.core.ports.repository;

import com.platform.fileservice.core.domain.model.TenantPolicy;

import java.util.Optional;

/**
 * Repository port for tenant policy definitions.
 */
public interface TenantPolicyRepository {

    Optional<TenantPolicy> findByTenantId(String tenantId);
}
