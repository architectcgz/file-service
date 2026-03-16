package com.platform.fileservice.core.domain.model;

/**
 * Aggregated usage metrics for a tenant.
 */
public record TenantUsage(
        String tenantId,
        long usedStorageBytes,
        long fileCount
) {
}
