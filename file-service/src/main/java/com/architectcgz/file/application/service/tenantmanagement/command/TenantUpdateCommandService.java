package com.architectcgz.file.application.service.tenantmanagement.command;

import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.application.service.tenantmanagement.audit.TenantManagementAuditService;
import com.architectcgz.file.application.service.tenantmanagement.mutation.TenantMutationService;
import com.architectcgz.file.application.service.tenantmanagement.persistence.TenantPersistenceService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantRecordQueryService;
import com.architectcgz.file.domain.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUpdateCommandService {

    private final TenantRecordQueryService tenantRecordQueryService;
    private final TenantMutationService tenantMutationService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantManagementAuditService tenantManagementAuditService;

    @Transactional
    public Tenant updateTenant(String tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRecordQueryService.loadTenantOrThrow(tenantId);
        Map<String, Object> changes = tenantMutationService.applyTenantUpdates(tenant, request);
        Tenant updatedTenant = tenantPersistenceService.saveTenant(tenant);
        tenantManagementAuditService.recordUpdateTenantAudit(tenantId, changes);

        log.info("Updated tenant: {}", tenantId);
        return updatedTenant;
    }
}
