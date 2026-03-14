package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;

/**
 * 租户已停用异常
 */
public class TenantSuspendedException extends BusinessException {
    public TenantSuspendedException(String tenantId) {
        super(FileServiceErrorCodes.TENANT_SUSPENDED, "Tenant is suspended: " + tenantId);
    }
}
