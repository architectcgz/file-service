package com.architectcgz.file.application.service.tenantmanagement.command;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.service.tenantmanagement.audit.TenantManagementAuditService;
import com.architectcgz.file.application.service.tenantmanagement.factory.TenantManagementObjectFactory;
import com.architectcgz.file.application.service.tenantmanagement.persistence.TenantPersistenceService;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCreateCommandService {

    private final TenantManagementObjectFactory tenantManagementObjectFactory;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantManagementAuditService tenantManagementAuditService;

    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        Tenant tenant = tenantManagementObjectFactory.buildTenant(request);
        Tenant savedTenant = tenantPersistenceService.saveTenant(tenant);

        TenantUsage tenantUsage = tenantManagementObjectFactory.buildTenantUsage(request.getTenantId());
        tenantPersistenceService.saveTenantUsage(tenantUsage);
        tenantManagementAuditService.recordCreateTenantAudit(savedTenant);

        log.info("Created tenant: {}", request.getTenantId());
        return savedTenant;
    }
}
