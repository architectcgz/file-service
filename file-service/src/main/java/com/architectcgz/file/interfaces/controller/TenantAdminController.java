package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.application.dto.UpdateTenantStatusRequest;
import com.architectcgz.file.application.service.TenantManagementService;
import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.domain.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {
    private final TenantManagementService tenantManagementService;

    /**
     * 创建租户
     */
    @PostMapping
    public ApiResponse<Tenant> createTenant(@RequestBody CreateTenantRequest request) {
        log.info("Creating tenant: {}", request.getTenantId());
        Tenant tenant = tenantManagementService.createTenant(request);
        return ApiResponse.success(tenant);
    }

    /**
     * 查询租户列表
     */
    @GetMapping
    public ApiResponse<List<Tenant>> listTenants() {
        log.info("Listing all tenants");
        List<Tenant> tenants = tenantManagementService.listTenants();
        return ApiResponse.success(tenants);
    }

    /**
     * 查询租户详情
     */
    @GetMapping("/{tenantId}")
    public ApiResponse<TenantDetailResponse> getTenantDetail(@PathVariable String tenantId) {
        log.info("Getting tenant detail: {}", tenantId);
        TenantDetailResponse detail = tenantManagementService.getTenantDetail(tenantId);
        return ApiResponse.success(detail);
    }

    /**
     * 更新租户
     */
    @PutMapping("/{tenantId}")
    public ApiResponse<Tenant> updateTenant(
            @PathVariable String tenantId,
            @RequestBody UpdateTenantRequest request) {
        log.info("Updating tenant: {}", tenantId);
        Tenant tenant = tenantManagementService.updateTenant(tenantId, request);
        return ApiResponse.success(tenant);
    }

    /**
     * 更新租户状态
     */
    @PutMapping("/{tenantId}/status")
    public ApiResponse<Void> updateTenantStatus(
            @PathVariable String tenantId,
            @RequestBody UpdateTenantStatusRequest request) {
        log.info("Updating tenant status: {} -> {}", tenantId, request.getStatus());
        tenantManagementService.updateTenantStatus(tenantId, request.getStatus());
        return ApiResponse.success(null);
    }

    /**
     * 删除租户（软删除）
     */
    @DeleteMapping("/{tenantId}")
    public ApiResponse<Void> deleteTenant(@PathVariable String tenantId) {
        log.info("Deleting tenant: {}", tenantId);
        tenantManagementService.deleteTenant(tenantId);
        return ApiResponse.success(null);
    }
}
