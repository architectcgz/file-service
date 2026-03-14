package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.application.service.tenantmanagement.audit.TenantManagementAuditService;
import com.architectcgz.file.application.service.tenantmanagement.command.TenantCreateCommandService;
import com.architectcgz.file.application.service.tenantmanagement.command.TenantDeleteCommandService;
import com.architectcgz.file.application.service.tenantmanagement.command.TenantStatusCommandService;
import com.architectcgz.file.application.service.tenantmanagement.command.TenantUpdateCommandService;
import com.architectcgz.file.application.service.tenantmanagement.factory.TenantManagementObjectFactory;
import com.architectcgz.file.application.service.tenantmanagement.mutation.TenantMutationService;
import com.architectcgz.file.application.service.tenantmanagement.persistence.TenantPersistenceService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantRecordQueryService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantDetailQueryService;
import com.architectcgz.file.application.service.tenantmanagement.query.TenantListQueryService;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户管理应用层门面。
 *
 * 对外保留租户管理原有入口，内部拆分为 command/query service。
 */
@Service
public class TenantManagementService {

    private final TenantCreateCommandService tenantCreateCommandService;
    private final TenantUpdateCommandService tenantUpdateCommandService;
    private final TenantStatusCommandService tenantStatusCommandService;
    private final TenantDeleteCommandService tenantDeleteCommandService;
    private final TenantDetailQueryService tenantDetailQueryService;
    private final TenantListQueryService tenantListQueryService;

    @Autowired
    public TenantManagementService(TenantCreateCommandService tenantCreateCommandService,
                                   TenantUpdateCommandService tenantUpdateCommandService,
                                   TenantStatusCommandService tenantStatusCommandService,
                                   TenantDeleteCommandService tenantDeleteCommandService,
                                   TenantDetailQueryService tenantDetailQueryService,
                                   TenantListQueryService tenantListQueryService) {
        this.tenantCreateCommandService = tenantCreateCommandService;
        this.tenantUpdateCommandService = tenantUpdateCommandService;
        this.tenantStatusCommandService = tenantStatusCommandService;
        this.tenantDeleteCommandService = tenantDeleteCommandService;
        this.tenantDetailQueryService = tenantDetailQueryService;
        this.tenantListQueryService = tenantListQueryService;
    }

    TenantManagementService(TenantRepository tenantRepository,
                            TenantUsageRepository tenantUsageRepository,
                            AuditLogService auditLogService) {
        this(
                buildLegacyCreateCommandService(tenantRepository, tenantUsageRepository, auditLogService),
                buildLegacyUpdateCommandService(tenantRepository, tenantUsageRepository, auditLogService),
                buildLegacyStatusCommandService(tenantRepository, tenantUsageRepository, auditLogService),
                buildLegacyDeleteCommandService(tenantRepository, tenantUsageRepository, auditLogService),
                buildLegacyDetailQueryService(tenantRepository, tenantUsageRepository),
                buildLegacyListQueryService(tenantRepository, tenantUsageRepository)
        );
    }

    public Tenant createTenant(CreateTenantRequest request) {
        return tenantCreateCommandService.createTenant(request);
    }

    public Tenant updateTenant(String tenantId, UpdateTenantRequest request) {
        return tenantUpdateCommandService.updateTenant(tenantId, request);
    }

    public void updateTenantStatus(String tenantId, TenantStatus newStatus) {
        tenantStatusCommandService.updateTenantStatus(tenantId, newStatus);
    }

    public void deleteTenant(String tenantId) {
        tenantDeleteCommandService.deleteTenant(tenantId);
    }

    public TenantDetailResponse getTenantDetail(String tenantId) {
        return tenantDetailQueryService.getTenantDetail(tenantId);
    }

    public List<Tenant> listTenants() {
        return tenantListQueryService.listTenants();
    }

    private static TenantCreateCommandService buildLegacyCreateCommandService(TenantRepository tenantRepository,
                                                                              TenantUsageRepository tenantUsageRepository,
                                                                              AuditLogService auditLogService) {
        TenantManagementObjectFactory objectFactory = new TenantManagementObjectFactory();
        TenantPersistenceService persistenceService = new TenantPersistenceService(tenantRepository, tenantUsageRepository);
        TenantManagementAuditService auditService = new TenantManagementAuditService(auditLogService);
        return new TenantCreateCommandService(objectFactory, persistenceService, auditService);
    }

    private static TenantUpdateCommandService buildLegacyUpdateCommandService(TenantRepository tenantRepository,
                                                                              TenantUsageRepository tenantUsageRepository,
                                                                              AuditLogService auditLogService) {
        TenantRecordQueryService queryService = new TenantRecordQueryService(tenantRepository, tenantUsageRepository);
        TenantMutationService mutationService = new TenantMutationService();
        TenantPersistenceService persistenceService = new TenantPersistenceService(tenantRepository, tenantUsageRepository);
        TenantManagementAuditService auditService = new TenantManagementAuditService(auditLogService);
        return new TenantUpdateCommandService(queryService, mutationService, persistenceService, auditService);
    }

    private static TenantStatusCommandService buildLegacyStatusCommandService(TenantRepository tenantRepository,
                                                                              TenantUsageRepository tenantUsageRepository,
                                                                              AuditLogService auditLogService) {
        TenantRecordQueryService queryService = new TenantRecordQueryService(tenantRepository, tenantUsageRepository);
        TenantMutationService mutationService = new TenantMutationService();
        TenantPersistenceService persistenceService = new TenantPersistenceService(tenantRepository, tenantUsageRepository);
        TenantManagementAuditService auditService = new TenantManagementAuditService(auditLogService);
        return new TenantStatusCommandService(queryService, mutationService, persistenceService, auditService);
    }

    private static TenantDeleteCommandService buildLegacyDeleteCommandService(TenantRepository tenantRepository,
                                                                              TenantUsageRepository tenantUsageRepository,
                                                                              AuditLogService auditLogService) {
        TenantRecordQueryService queryService = new TenantRecordQueryService(tenantRepository, tenantUsageRepository);
        TenantMutationService mutationService = new TenantMutationService();
        TenantPersistenceService persistenceService = new TenantPersistenceService(tenantRepository, tenantUsageRepository);
        TenantManagementAuditService auditService = new TenantManagementAuditService(auditLogService);
        return new TenantDeleteCommandService(queryService, mutationService, persistenceService, auditService);
    }

    private static TenantDetailQueryService buildLegacyDetailQueryService(TenantRepository tenantRepository,
                                                                          TenantUsageRepository tenantUsageRepository) {
        return new TenantDetailQueryService(
                new TenantRecordQueryService(tenantRepository, tenantUsageRepository),
                new TenantManagementObjectFactory()
        );
    }

    private static TenantListQueryService buildLegacyListQueryService(TenantRepository tenantRepository,
                                                                      TenantUsageRepository tenantUsageRepository) {
        return new TenantListQueryService(new TenantRecordQueryService(tenantRepository, tenantUsageRepository));
    }
}
