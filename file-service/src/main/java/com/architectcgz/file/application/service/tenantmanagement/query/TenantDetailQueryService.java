package com.architectcgz.file.application.service.tenantmanagement.query;

import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.application.service.tenantmanagement.factory.TenantManagementObjectFactory;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantDetailQueryService {

    private final TenantRecordQueryService tenantRecordQueryService;
    private final TenantManagementObjectFactory tenantManagementObjectFactory;

    public TenantDetailResponse getTenantDetail(String tenantId) {
        Tenant tenant = tenantRecordQueryService.loadTenantOrThrow(tenantId);
        TenantUsage tenantUsage = tenantRecordQueryService.loadTenantUsageOrDefault(tenantId);
        return tenantManagementObjectFactory.buildTenantDetail(tenant, tenantUsage);
    }
}
