package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.domain.model.*;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final AuditLogService auditLogService;

    /**
     * 创建租户
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(request.getTenantId());
        tenant.setTenantName(request.getTenantName());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxStorageBytes(request.getMaxStorageBytes());
        tenant.setMaxFileCount(request.getMaxFileCount());
        tenant.setMaxSingleFileSize(request.getMaxSingleFileSize());
        tenant.setAllowedFileTypes(request.getAllowedFileTypes());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());

        Tenant savedTenant = tenantRepository.save(tenant);

        // 同时创建租户使用统计记录
        TenantUsage usage = new TenantUsage(request.getTenantId());
        tenantUsageRepository.save(usage);

        // 记录审计日志
        recordCreateTenantAudit(savedTenant);

        log.info("Created tenant: {}", request.getTenantId());
        return savedTenant;
    }

    /**
     * 更新租户
     */
    @Transactional
    public Tenant updateTenant(String tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Map<String, Object> changes = new HashMap<>();

        // 验证配额值的有效性
        if (request.getMaxStorageBytes() != null) {
            if (request.getMaxStorageBytes() <= 0) {
                throw new IllegalArgumentException(
                    "Invalid maxStorageBytes: " + request.getMaxStorageBytes() + 
                    ". Value must be positive."
                );
            }
            changes.put("maxStorageBytes", Map.of(
                "old", tenant.getMaxStorageBytes(),
                "new", request.getMaxStorageBytes()
            ));
            tenant.setMaxStorageBytes(request.getMaxStorageBytes());
        }
        
        if (request.getMaxFileCount() != null) {
            if (request.getMaxFileCount() <= 0) {
                throw new IllegalArgumentException(
                    "Invalid maxFileCount: " + request.getMaxFileCount() + 
                    ". Value must be positive."
                );
            }
            changes.put("maxFileCount", Map.of(
                "old", tenant.getMaxFileCount(),
                "new", request.getMaxFileCount()
            ));
            tenant.setMaxFileCount(request.getMaxFileCount());
        }
        
        if (request.getMaxSingleFileSize() != null) {
            if (request.getMaxSingleFileSize() <= 0) {
                throw new IllegalArgumentException(
                    "Invalid maxSingleFileSize: " + request.getMaxSingleFileSize() + 
                    ". Value must be positive."
                );
            }
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
        tenant.setUpdatedAt(LocalDateTime.now());

        Tenant updatedTenant = tenantRepository.save(tenant);
        
        // 记录审计日志
        recordUpdateTenantAudit(tenantId, changes);
        
        log.info("Updated tenant: {}", tenantId);
        return updatedTenant;
    }

    /**
     * 更新租户状态
     */
    @Transactional
    public void updateTenantStatus(String tenantId, TenantStatus newStatus) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

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
        }

        tenantRepository.save(tenant);
        
        // 记录审计日志
        recordUpdateTenantStatusAudit(tenantId, oldStatus, newStatus);
        
        log.info("Updated tenant status: {} -> {}", tenantId, newStatus);
    }

    /**
     * 删除租户（软删除）
     */
    @Transactional
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        tenant.markDeleted();
        tenantRepository.save(tenant);
        
        // 记录审计日志
        recordDeleteTenantAudit(tenantId);
        
        log.info("Soft deleted tenant: {}", tenantId);
    }

    /**
     * 获取租户详情
     */
    public TenantDetailResponse getTenantDetail(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        TenantUsage usage = tenantUsageRepository.findById(tenantId)
                .orElse(new TenantUsage(tenantId));

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

        // 计算使用百分比
        if (tenant.getMaxStorageBytes() > 0) {
            response.setStorageUsagePercent(
                (usage.getUsedStorageBytes() * 100.0) / tenant.getMaxStorageBytes()
            );
        }
        if (tenant.getMaxFileCount() > 0) {
            response.setFileCountUsagePercent(
                (usage.getUsedFileCount() * 100.0) / tenant.getMaxFileCount()
            );
        }

        return response;
    }

    /**
     * 查询租户列表
     */
    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }
    
    /**
     * 记录创建租户的审计日志
     */
    private void recordCreateTenantAudit(Tenant tenant) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenantName", tenant.getTenantName());
        details.put("maxStorageBytes", tenant.getMaxStorageBytes());
        details.put("maxFileCount", tenant.getMaxFileCount());
        details.put("maxSingleFileSize", tenant.getMaxSingleFileSize());
        details.put("contactEmail", tenant.getContactEmail());
        
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(AdminContext.getAdminUser())
                .action(AuditAction.CREATE_TENANT)
                .targetType(TargetType.TENANT)
                .targetId(tenant.getTenantId())
                .tenantId(tenant.getTenantId())
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
    
    /**
     * 记录更新租户的审计日志
     */
    private void recordUpdateTenantAudit(String tenantId, Map<String, Object> changes) {
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(AdminContext.getAdminUser())
                .action(AuditAction.UPDATE_TENANT)
                .targetType(TargetType.TENANT)
                .targetId(tenantId)
                .tenantId(tenantId)
                .details(changes)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
    
    /**
     * 记录更新租户状态的审计日志
     */
    private void recordUpdateTenantStatusAudit(String tenantId, TenantStatus oldStatus, TenantStatus newStatus) {
        Map<String, Object> details = new HashMap<>();
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", newStatus.name());
        
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(AdminContext.getAdminUser())
                .action(AuditAction.SUSPEND_TENANT)
                .targetType(TargetType.TENANT)
                .targetId(tenantId)
                .tenantId(tenantId)
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
    
    /**
     * 记录删除租户的审计日志
     */
    private void recordDeleteTenantAudit(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put("deletionType", "soft_delete");
        
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(AdminContext.getAdminUser())
                .action(AuditAction.SUSPEND_TENANT)
                .targetType(TargetType.TENANT)
                .targetId(tenantId)
                .tenantId(tenantId)
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
}
