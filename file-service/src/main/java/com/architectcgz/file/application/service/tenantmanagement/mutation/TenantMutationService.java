package com.architectcgz.file.application.service.tenantmanagement.mutation;

import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * 租户变更服务。
 */
@Service
public class TenantMutationService {

    public Map<String, Object> applyTenantUpdates(Tenant tenant, UpdateTenantRequest request) {
        Map<String, Object> changes = new HashMap<>();

        if (request.getMaxStorageBytes() != null) {
            validatePositiveLong("maxStorageBytes", request.getMaxStorageBytes());
            changes.put("maxStorageBytes", Map.of(
                    "old", tenant.getMaxStorageBytes(),
                    "new", request.getMaxStorageBytes()
            ));
            tenant.setMaxStorageBytes(request.getMaxStorageBytes());
        }

        if (request.getMaxFileCount() != null) {
            validatePositiveInteger("maxFileCount", request.getMaxFileCount());
            changes.put("maxFileCount", Map.of(
                    "old", tenant.getMaxFileCount(),
                    "new", request.getMaxFileCount()
            ));
            tenant.setMaxFileCount(request.getMaxFileCount());
        }

        if (request.getMaxSingleFileSize() != null) {
            validatePositiveLong("maxSingleFileSize", request.getMaxSingleFileSize());
            changes.put("maxSingleFileSize", Map.of(
                    "old", tenant.getMaxSingleFileSize(),
                    "new", request.getMaxSingleFileSize()
            ));
            tenant.setMaxSingleFileSize(request.getMaxSingleFileSize());
        }

        if (request.getTenantName() != null) {
            changes.put("tenantName", Map.of(
                    "old", tenant.getTenantName(),
                    "new", request.getTenantName()
            ));
            tenant.setTenantName(request.getTenantName());
        }

        if (request.getAllowedFileTypes() != null) {
            changes.put("allowedFileTypes", Map.of(
                    "old", tenant.getAllowedFileTypes(),
                    "new", request.getAllowedFileTypes()
            ));
            tenant.setAllowedFileTypes(request.getAllowedFileTypes());
        }

        if (request.getContactEmail() != null) {
            changes.put("contactEmail", Map.of(
                    "old", tenant.getContactEmail(),
                    "new", request.getContactEmail()
            ));
            tenant.setContactEmail(request.getContactEmail());
        }

        tenant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return changes;
    }

    public TenantStatus applyTenantStatus(Tenant tenant, TenantStatus newStatus) {
        TenantStatus oldStatus = tenant.getStatus();
        switch (newStatus) {
            case ACTIVE:
                tenant.activate();
                break;
            case SUSPENDED:
                tenant.suspend();
                break;
            case DELETED:
                tenant.markDeleted();
                break;
            default:
                throw new IllegalArgumentException("Unsupported tenant status: " + newStatus);
        }
        return oldStatus;
    }

    public void markTenantDeleted(Tenant tenant) {
        tenant.markDeleted();
    }

    private void validatePositiveLong(String fieldName, Long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + ": " + value + ". Value must be positive."
            );
        }
    }

    private void validatePositiveInteger(String fieldName, Integer value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + ": " + value + ". Value must be positive."
            );
        }
    }
}
