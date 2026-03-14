package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;

/**
 * 租户不存在异常
 */
public class TenantNotFoundException extends BusinessException {
    public TenantNotFoundException(String tenantId) {
        super(FileServiceErrorCodes.TENANT_NOT_FOUND, "Tenant not found: " + tenantId);
    }
}
