package com.platform.fileservice.core.domain.model;

/**
 * File service quota and policy settings for a tenant.
 */
public record TenantPolicy(
        String tenantId,
        long maxStorageBytes,
        long maxFileCount,
        long maxSingleFileSize,
        boolean autoProvisioned
) {
}
