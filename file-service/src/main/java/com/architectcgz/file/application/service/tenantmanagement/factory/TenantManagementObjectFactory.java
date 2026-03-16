package com.architectcgz.file.application.service.tenantmanagement.factory;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.model.TenantUsage;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 租户管理对象工厂。
 */
@Component
public class TenantManagementObjectFactory {

    public Tenant buildTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(request.getTenantId());
        tenant.setTenantName(request.getTenantName());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxStorageBytes(request.getMaxStorageBytes());
        tenant.setMaxFileCount(request.getMaxFileCount());
        tenant.setMaxSingleFileSize(request.getMaxSingleFileSize());
        tenant.setAllowedFileTypes(request.getAllowedFileTypes());
        tenant.setContactEmail(request.getContactEmail());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        return tenant;
    }

    public TenantUsage buildTenantUsage(String tenantId) {
        return new TenantUsage(tenantId);
    }

    public TenantDetailResponse buildTenantDetail(Tenant tenant, TenantUsage usage) {
        TenantDetailResponse response = new TenantDetailResponse();
        response.setTenantId(tenant.getTenantId());
        response.setTenantName(tenant.getTenantName());
        response.setStatus(tenant.getStatus());
        response.setMaxStorageBytes(tenant.getMaxStorageBytes());
        response.setMaxFileCount(tenant.getMaxFileCount());
        response.setMaxSingleFileSize(tenant.getMaxSingleFileSize());
        response.setAllowedFileTypes(tenant.getAllowedFileTypes());
        response.setContactEmail(tenant.getContactEmail());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setUpdatedAt(tenant.getUpdatedAt());
        response.setUsedStorageBytes(usage.getUsedStorageBytes());
        response.setUsedFileCount(usage.getUsedFileCount());
        response.setLastUploadAt(usage.getLastUploadAt());

        if (tenant.getMaxStorageBytes() > 0) {
            response.setStorageUsagePercent((usage.getUsedStorageBytes() * 100.0) / tenant.getMaxStorageBytes());
        }
        if (tenant.getMaxFileCount() > 0) {
            response.setFileCountUsagePercent((usage.getUsedFileCount() * 100.0) / tenant.getMaxFileCount());
        }
        return response;
    }
}
