package com.architectcgz.file.common.exception;

/**
 * 租户不存在异常
 */
public class TenantNotFoundException extends BusinessException {
    public TenantNotFoundException(String tenantId) {
        super("TENANT_NOT_FOUND", "Tenant not found: " + tenantId);
    }
}
