package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.CreateTenantRequest;
import com.architectcgz.file.application.dto.TenantDetailResponse;
import com.architectcgz.file.application.dto.UpdateTenantRequest;
import com.architectcgz.file.application.dto.UpdateTenantStatusRequest;
import com.architectcgz.file.application.service.TenantManagementService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tenant Admin Controller Test
 */
@WebMvcTest(controllers = TenantAdminController.class, excludeAutoConfiguration = {
    MybatisAutoConfiguration.class,
    DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class TenantAdminControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private TenantManagementService tenantManagementService;

    @BeforeEach
    void setUp() {
        AdminContext.setAdminUser("admin-1");
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }
    
    /**
     * 测试创建租户接口
     */
    @Test
    void testCreateTenant() throws Exception {
        // 准备测试数据
        CreateTenantRequest request = new CreateTenantRequest();
        request.setTenantId("test-tenant");
        request.setTenantName("Test Tenant");
        request.setMaxStorageBytes(10737418240L); // 10GB
        request.setMaxFileCount(10000);
        request.setMaxSingleFileSize(104857600L); // 100MB
        request.setAllowedFileTypes(Arrays.asList("image/jpeg", "image/png"));
        request.setContactEmail("test@example.com");
        
        Tenant tenant = new Tenant();
        tenant.setTenantId("test-tenant");
        tenant.setTenantName("Test Tenant");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxStorageBytes(10737418240L);
        tenant.setMaxFileCount(10000);
        tenant.setMaxSingleFileSize(104857600L);
        tenant.setAllowedFileTypes(Arrays.asList("image/jpeg", "image/png"));
        tenant.setContactEmail("test@example.com");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        
        when(tenantManagementService.createTenant(any(CreateTenantRequest.class)))
                .thenReturn(tenant);
        
        // 执行测试
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value("test-tenant"))
                .andExpect(jsonPath("$.data.tenantName").value("Test Tenant"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maxStorageBytes").value(10737418240L))
                .andExpect(jsonPath("$.data.maxFileCount").value(10000))
                .andExpect(jsonPath("$.data.maxSingleFileSize").value(104857600L))
                .andExpect(jsonPath("$.data.contactEmail").value("test@example.com"));
    }
    
    /**
     * 测试查询租户列表接口
     */
    @Test
    void testListTenants() throws Exception {
        // 准备测试数据
        Tenant tenant1 = new Tenant();
        tenant1.setTenantId("tenant-1");
        tenant1.setTenantName("Tenant 1");
        tenant1.setStatus(TenantStatus.ACTIVE);
        tenant1.setMaxStorageBytes(10737418240L);
        tenant1.setMaxFileCount(10000);
        tenant1.setMaxSingleFileSize(104857600L);
        tenant1.setCreatedAt(LocalDateTime.now());
        tenant1.setUpdatedAt(LocalDateTime.now());
        
        Tenant tenant2 = new Tenant();
        tenant2.setTenantId("tenant-2");
        tenant2.setTenantName("Tenant 2");
        tenant2.setStatus(TenantStatus.SUSPENDED);
        tenant2.setMaxStorageBytes(5368709120L);
        tenant2.setMaxFileCount(5000);
        tenant2.setMaxSingleFileSize(52428800L);
        tenant2.setCreatedAt(LocalDateTime.now());
        tenant2.setUpdatedAt(LocalDateTime.now());
        
        List<Tenant> tenants = Arrays.asList(tenant1, tenant2);
        
        when(tenantManagementService.listTenants())
                .thenReturn(tenants);
        
        // 执行测试
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].tenantId").value("tenant-2"))
                .andExpect(jsonPath("$.data[1].status").value("SUSPENDED"));
    }
    
    /**
     * 测试查询空租户列表
     */
    @Test
    void testListTenantsEmpty() throws Exception {
        when(tenantManagementService.listTenants())
                .thenReturn(Arrays.asList());
        
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
    
    /**
     * 测试查询租户详情接口
     */
    @Test
    void testGetTenantDetail() throws Exception {
        // 准备测试数据
        TenantDetailResponse detail = new TenantDetailResponse();
        detail.setTenantId("test-tenant");
        detail.setTenantName("Test Tenant");
        detail.setStatus(TenantStatus.ACTIVE);
        detail.setMaxStorageBytes(10737418240L);
        detail.setMaxFileCount(10000);
        detail.setMaxSingleFileSize(104857600L);
        detail.setAllowedFileTypes(Arrays.asList("image/jpeg", "image/png"));
        detail.setContactEmail("test@example.com");
        detail.setCreatedAt(LocalDateTime.now());
        detail.setUpdatedAt(LocalDateTime.now());
        detail.setUsedStorageBytes(5368709120L);
        detail.setUsedFileCount(500);
        detail.setLastUploadAt(LocalDateTime.now());
        detail.setStorageUsagePercent(50.0);
        detail.setFileCountUsagePercent(5.0);
        
        when(tenantManagementService.getTenantDetail("test-tenant"))
                .thenReturn(detail);
        
        // 执行测试
        mockMvc.perform(get("/api/v1/admin/tenants/test-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value("test-tenant"))
                .andExpect(jsonPath("$.data.tenantName").value("Test Tenant"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.usedStorageBytes").value(5368709120L))
                .andExpect(jsonPath("$.data.usedFileCount").value(500))
                .andExpect(jsonPath("$.data.storageUsagePercent").value(50.0))
                .andExpect(jsonPath("$.data.fileCountUsagePercent").value(5.0));
    }
    
    /**
     * 测试查询不存在的租户详情
     */
    @Test
    void testGetTenantDetailNotFound() throws Exception {
        when(tenantManagementService.getTenantDetail("non-existent"))
                .thenThrow(new TenantNotFoundException("non-existent"));
        
        mockMvc.perform(get("/api/v1/admin/tenants/non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isNotFound());
    }
    
    /**
     * 测试更新租户接口
     */
    @Test
    void testUpdateTenant() throws Exception {
        // 准备测试数据
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setTenantName("Updated Tenant Name");
        request.setMaxStorageBytes(21474836480L); // 20GB
        request.setMaxFileCount(20000);
        request.setMaxSingleFileSize(209715200L); // 200MB
        request.setAllowedFileTypes(Arrays.asList("image/jpeg", "image/png", "video/mp4"));
        request.setContactEmail("updated@example.com");
        
        Tenant updatedTenant = new Tenant();
        updatedTenant.setTenantId("test-tenant");
        updatedTenant.setTenantName("Updated Tenant Name");
        updatedTenant.setStatus(TenantStatus.ACTIVE);
        updatedTenant.setMaxStorageBytes(21474836480L);
        updatedTenant.setMaxFileCount(20000);
        updatedTenant.setMaxSingleFileSize(209715200L);
        updatedTenant.setAllowedFileTypes(Arrays.asList("image/jpeg", "image/png", "video/mp4"));
        updatedTenant.setContactEmail("updated@example.com");
        updatedTenant.setCreatedAt(LocalDateTime.now().minusDays(1));
        updatedTenant.setUpdatedAt(LocalDateTime.now());
        
        when(tenantManagementService.updateTenant(eq("test-tenant"), any(UpdateTenantRequest.class)))
                .thenReturn(updatedTenant);
        
        // 执行测试
        mockMvc.perform(put("/api/v1/admin/tenants/test-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value("test-tenant"))
                .andExpect(jsonPath("$.data.tenantName").value("Updated Tenant Name"))
                .andExpect(jsonPath("$.data.maxStorageBytes").value(21474836480L))
                .andExpect(jsonPath("$.data.maxFileCount").value(20000))
                .andExpect(jsonPath("$.data.maxSingleFileSize").value(209715200L))
                .andExpect(jsonPath("$.data.contactEmail").value("updated@example.com"));
    }
    
    /**
     * 测试更新不存在的租户
     */
    @Test
    void testUpdateTenantNotFound() throws Exception {
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setTenantName("Updated Name");
        
        when(tenantManagementService.updateTenant(eq("non-existent"), any(UpdateTenantRequest.class)))
                .thenThrow(new TenantNotFoundException("non-existent"));
        
        mockMvc.perform(put("/api/v1/admin/tenants/non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
    
    /**
     * 测试更新租户状态接口 - 停用
     */
    @Test
    void testUpdateTenantStatusToSuspended() throws Exception {
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest();
        request.setStatus(TenantStatus.SUSPENDED);
        
        // 执行测试
        mockMvc.perform(put("/api/v1/admin/tenants/test-tenant/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    
    /**
     * 测试更新租户状态接口 - 激活
     */
    @Test
    void testUpdateTenantStatusToActive() throws Exception {
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest();
        request.setStatus(TenantStatus.ACTIVE);
        
        // 执行测试
        mockMvc.perform(put("/api/v1/admin/tenants/test-tenant/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    
    /**
     * 测试更新不存在租户的状态
     */
    @Test
    void testUpdateTenantStatusNotFound() throws Exception {
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest();
        request.setStatus(TenantStatus.SUSPENDED);
        
        doThrow(new TenantNotFoundException("non-existent"))
                .when(tenantManagementService).updateTenantStatus("non-existent", TenantStatus.SUSPENDED);
        
        mockMvc.perform(put("/api/v1/admin/tenants/non-existent/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
    
    /**
     * 测试删除租户接口（软删除）
     */
    @Test
    void testDeleteTenant() throws Exception {
        // 执行测试
        mockMvc.perform(delete("/api/v1/admin/tenants/test-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    
    /**
     * 测试删除不存在的租户
     */
    @Test
    void testDeleteTenantNotFound() throws Exception {
        doThrow(new TenantNotFoundException("non-existent"))
                .when(tenantManagementService).deleteTenant("non-existent");
        
        mockMvc.perform(delete("/api/v1/admin/tenants/non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListTenantsWithoutAdminIdentityReturnsForbidden() throws Exception {
        AdminContext.clear();

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(tenantManagementService);
    }
}
