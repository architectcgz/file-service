package com.architectcgz.file.common.exception;

/**
 * 租户已停用异常
 */
public class TenantSuspendedException extends BusinessException {
    public TenantSuspendedException(String tenantId) {
        super("TENANT_SUSPENDED", "Tenant is suspended: " + tenantId);
    }
}
