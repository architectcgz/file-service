package com.architectcgz.file.application.service.tenantmanagement.audit;

import com.architectcgz.file.application.service.AuditLogService;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.domain.model.AuditAction;
import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.model.TargetType;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 租户管理审计服务。
 */
@Service
@RequiredArgsConstructor
public class TenantManagementAuditService {

    private final AuditLogService auditLogService;

    public void recordCreateTenantAudit(Tenant tenant) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenantName", tenant.getTenantName());
        details.put("maxStorageBytes", tenant.getMaxStorageBytes());
        details.put("maxFileCount", tenant.getMaxFileCount());
        details.put("maxSingleFileSize", tenant.getMaxSingleFileSize());
        details.put("contactEmail", tenant.getContactEmail());

        auditLogService.log(buildAuditLog(
                AuditAction.CREATE_TENANT,
                tenant.getTenantId(),
                tenant.getTenantId(),
                details
        ));
    }

    public void recordUpdateTenantAudit(String tenantId, Map<String, Object> changes) {
        auditLogService.log(buildAuditLog(
                AuditAction.UPDATE_TENANT,
                tenantId,
                tenantId,
                changes
        ));
    }

    public void recordUpdateTenantStatusAudit(String tenantId, TenantStatus oldStatus, TenantStatus newStatus) {
        Map<String, Object> details = new HashMap<>();
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", newStatus.name());

        auditLogService.log(buildAuditLog(
                AuditAction.SUSPEND_TENANT,
                tenantId,
                tenantId,
                details
        ));
    }

    public void recordDeleteTenantAudit(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put("deletionType", "soft_delete");

        auditLogService.log(buildAuditLog(
                AuditAction.SUSPEND_TENANT,
                tenantId,
                tenantId,
                details
        ));
    }

    private AuditLog buildAuditLog(AuditAction action,
                                   String targetId,
                                   String tenantId,
                                   Map<String, Object> details) {
        return AuditLog.builder()
                .adminUserId(AdminContext.getAdminUser())
                .action(action)
                .targetType(TargetType.TENANT)
                .targetId(targetId)
                .tenantId(tenantId)
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
    }
}
