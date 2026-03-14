package com.architectcgz.file.application.service.tenantmanagement.command;

import com.architectcgz.file.application.service.tenantmanagement.audit.TenantManagementAuditService;
import com.architectcgz.file.application.service.tenantmanagement.mutation.TenantMutationService;
import com.architectcgz.file.application.service.tenantmanagement.persistence.TenantPersistenceService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantRecordQueryService;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantStatusCommandService {

    private final TenantRecordQueryService tenantRecordQueryService;
    private final TenantMutationService tenantMutationService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantManagementAuditService tenantManagementAuditService;

    @Transactional
    public void updateTenantStatus(String tenantId, TenantStatus newStatus) {
        Tenant tenant = tenantRecordQueryService.loadTenantOrThrow(tenantId);
        TenantStatus oldStatus = tenantMutationService.applyTenantStatus(tenant, newStatus);
        tenantPersistenceService.saveTenant(tenant);
        tenantManagementAuditService.recordUpdateTenantStatusAudit(tenantId, oldStatus, newStatus);

        log.info("Updated tenant status: {} -> {}", tenantId, newStatus);
    }
}
