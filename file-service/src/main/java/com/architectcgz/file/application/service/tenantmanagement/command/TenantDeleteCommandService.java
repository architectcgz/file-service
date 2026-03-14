package com.architectcgz.file.application.service.tenantmanagement.command;

import com.architectcgz.file.application.service.tenantmanagement.audit.TenantManagementAuditService;
import com.architectcgz.file.application.service.tenantmanagement.mutation.TenantMutationService;
import com.architectcgz.file.application.service.tenantmanagement.persistence.TenantPersistenceService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantRecordQueryService;
import com.architectcgz.file.domain.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDeleteCommandService {

    private final TenantRecordQueryService tenantRecordQueryService;
    private final TenantMutationService tenantMutationService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantManagementAuditService tenantManagementAuditService;

    @Transactional
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantRecordQueryService.loadTenantOrThrow(tenantId);
        tenantMutationService.markTenantDeleted(tenant);
        tenantPersistenceService.saveTenant(tenant);
        tenantManagementAuditService.recordDeleteTenantAudit(tenantId);

        log.info("Soft deleted tenant: {}", tenantId);
    }
}
