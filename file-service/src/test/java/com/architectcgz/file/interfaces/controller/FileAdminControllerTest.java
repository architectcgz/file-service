package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.*;
import com.architectcgz.file.application.service.FileManagementService;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * File Admin Controller Test
 */
@WebMvcTest(controllers = FileAdminController.class, 
    excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class FileAdminControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private FileManagementService fileManagementService;

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }
    
    @Test
    void testListFilesWithoutFilters() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        FileRecord file2 = createFileRecord("file-2", "tenant-1", "user-2", AccessLevel.PRIVATE);
        
        List<FileRecord> files = Arrays.asList(file1, file2);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 2);
        
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/admin/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value("file-1"))
                .andExpect(jsonPath("$.data.content[1].id").value("file-2"));
    }
    
    @Test
    void testListFilesByTenant() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        FileRecord file2 = createFileRecord("file-2", "tenant-1", "user-2", AccessLevel.PUBLIC);
        
        List<FileRecord> files = Arrays.asList(file1, file2);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 2);
        
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/admin/files")
                        .param("tenantId", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }
    
    @Test
    void testListFilesByUser() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value("user-1"));
    }
    
    @Test
    void testListFilesByContentType() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        file1.setContentType("image/jpeg");
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("contentType", "image/jpeg")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].contentType").value("image/jpeg"));
    }
    
    @Test
    void testListFilesByAccessLevel() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PRIVATE);
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("accessLevel", "PRIVATE")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accessLevel").value("PRIVATE"));
    }
    
    @Test
    void testListFilesByTimeRange() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("startTime", "2026-01-01T00:00:00")
                        .param("endTime", "2026-12-31T23:59:59")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
    
    @Test
    void testListFilesBySizeRange() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        file1.setFileSize(1024000L);
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("minSize", "1000000").param("maxSize", "2000000")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
    
    @Test
    void testListFilesWithMultipleFilters() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        file1.setContentType("image/jpeg");
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 1);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("tenantId", "tenant-1").param("userId", "user-1")
                        .param("contentType", "image/jpeg").param("accessLevel", "PUBLIC")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
    
    @Test
    void testListFilesWithPagination() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        List<FileRecord> files = Arrays.asList(file1);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 1, 10, 25);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("page", "1").param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(25));
    }
    
    @Test
    void testListFilesWithSorting() throws Exception {
        FileRecord file1 = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        FileRecord file2 = createFileRecord("file-2", "tenant-1", "user-2", AccessLevel.PUBLIC);
        List<FileRecord> files = Arrays.asList(file1, file2);
        PageResponse<FileRecord> pageResponse = PageResponse.of(files, 0, 20, 2);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files").param("sortBy", "fileSize").param("sortOrder", "asc")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }
    
    @Test
    void testListFilesEmpty() throws Exception {
        PageResponse<FileRecord> pageResponse = PageResponse.of(Collections.emptyList(), 0, 20, 0);
        when(fileManagementService.listFiles(any(FileQuery.class))).thenReturn(pageResponse);
        mockMvc.perform(get("/api/v1/admin/files")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
    
    @Test
    void testGetFileDetail() throws Exception {
        FileRecord file = createFileRecord("file-1", "tenant-1", "user-1", AccessLevel.PUBLIC);
        file.setOriginalFilename("test-image.jpg");
        file.setContentType("image/jpeg");
        file.setFileSize(1024000L);
        file.setFileHash("abc123def456");
        file.setHashAlgorithm("MD5");
        when(fileManagementService.getFileDetail("file-1")).thenReturn(file);
        mockMvc.perform(get("/api/v1/admin/files/file-1")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("file-1"))
                .andExpect(jsonPath("$.data.appId").value("tenant-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.originalFilename").value("test-image.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.fileSize").value(1024000))
                .andExpect(jsonPath("$.data.fileHash").value("abc123def456"))
                .andExpect(jsonPath("$.data.hashAlgorithm").value("MD5"))
                .andExpect(jsonPath("$.data.accessLevel").value("PUBLIC"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
    
    @Test
    void testGetFileDetailNotFound() throws Exception {
        when(fileManagementService.getFileDetail("non-existent"))
                .thenThrow(new BusinessException("FILE_NOT_FOUND", "File not found: non-existent"));
        mockMvc.perform(get("/api/v1/admin/files/non-existent")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").value("File not found: non-existent"));
    }
    
    @Test
    void testDeleteFile() throws Exception {
        AdminContext.setAdminUser("admin-1");
        mockMvc.perform(delete("/api/v1/admin/files/file-1")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
    
    @Test
    void testDeleteFileNotFound() throws Exception {
        AdminContext.setAdminUser("admin-1");
        doThrow(new BusinessException("FILE_NOT_FOUND", "File not found: non-existent"))
                .when(fileManagementService).deleteFile(eq("non-existent"), eq("admin-1"));
        mockMvc.perform(delete("/api/v1/admin/files/non-existent")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteFileWithoutAdminIdentityReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/files/file-1")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(fileManagementService);
    }

    @Test
    void testDeleteFileUsesAdminContext() throws Exception {
        AdminContext.setAdminUser("admin-ctx");

        mockMvc.perform(delete("/api/v1/admin/files/file-1")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fileManagementService).deleteFile("file-1", "admin-ctx");
    }
    
    @Test
    void testBatchDeleteFilesAllSuccess() throws Exception {
        AdminContext.setAdminUser("admin-1");
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setFileIds(Arrays.asList("file-1", "file-2", "file-3"));
        BatchDeleteResult result = new BatchDeleteResult();
        result.setTotalRequested(3);
        result.setSuccessCount(3);
        result.setFailureCount(0);
        result.setFailures(Collections.emptyList());
        when(fileManagementService.batchDeleteFiles(anyList(), eq("admin-1"))).thenReturn(result);
        mockMvc.perform(post("/api/v1/admin/files/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRequested").value(3))
                .andExpect(jsonPath("$.data.successCount").value(3))
                .andExpect(jsonPath("$.data.failureCount").value(0))
                .andExpect(jsonPath("$.data.failures").isArray())
                .andExpect(jsonPath("$.data.failures.length()").value(0));
    }
    
    @Test
    void testBatchDeleteFilesPartialFailure() throws Exception {
        AdminContext.setAdminUser("admin-1");
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setFileIds(Arrays.asList("file-1", "file-2", "file-3"));
        BatchDeleteResult.DeleteFailure failure = new BatchDeleteResult.DeleteFailure();
        failure.setFileId("file-2");
        failure.setReason("File not found");
        BatchDeleteResult result = new BatchDeleteResult();
        result.setTotalRequested(3);
        result.setSuccessCount(2);
        result.setFailureCount(1);
        result.setFailures(Arrays.asList(failure));
        when(fileManagementService.batchDeleteFiles(anyList(), eq("admin-1"))).thenReturn(result);
        mockMvc.perform(post("/api/v1/admin/files/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRequested").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.failures.length()").value(1))
                .andExpect(jsonPath("$.data.failures[0].fileId").value("file-2"))
                .andExpect(jsonPath("$.data.failures[0].reason").value("File not found"));
    }
    
    @Test
    void testBatchDeleteFilesEmptyList() throws Exception {
        AdminContext.setAdminUser("admin-1");
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setFileIds(Collections.emptyList());
        BatchDeleteResult result = new BatchDeleteResult();
        result.setTotalRequested(0);
        result.setSuccessCount(0);
        result.setFailureCount(0);
        result.setFailures(Collections.emptyList());
        when(fileManagementService.batchDeleteFiles(anyList(), eq("admin-1"))).thenReturn(result);
        mockMvc.perform(post("/api/v1/admin/files/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRequested").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    @Test
    void testBatchDeleteFilesWithoutAdminIdentityReturnsForbidden() throws Exception {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setFileIds(Collections.singletonList("file-1"));

        mockMvc.perform(post("/api/v1/admin/files/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(fileManagementService);
    }
    
    @Test
    void testGetStatistics() throws Exception {
        Map<String, Long> filesByType = new HashMap<>();
        filesByType.put("image/jpeg", 100L);
        filesByType.put("image/png", 50L);
        filesByType.put("application/pdf", 30L);
        Map<String, Long> storageByTenant = new HashMap<>();
        storageByTenant.put("tenant-1", 5368709120L);
        storageByTenant.put("tenant-2", 2684354560L);
        StorageStatistics statistics = StorageStatistics.builder()
                .totalFiles(180L).totalStorageBytes(8053063680L)
                .publicFiles(120L).privateFiles(60L)
                .filesByType(filesByType).storageByTenant(storageByTenant)
                .statisticsTime(LocalDateTime.now()).build();
        when(fileManagementService.getStorageStatistics()).thenReturn(statistics);
        mockMvc.perform(get("/api/v1/admin/files/statistics")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalFiles").value(180))
                .andExpect(jsonPath("$.data.totalStorageBytes").value(8053063680L))
                .andExpect(jsonPath("$.data.publicFiles").value(120))
                .andExpect(jsonPath("$.data.privateFiles").value(60))
                .andExpect(jsonPath("$.data.filesByType['image/jpeg']").value(100))
                .andExpect(jsonPath("$.data.filesByType['image/png']").value(50))
                .andExpect(jsonPath("$.data.filesByType['application/pdf']").value(30))
                .andExpect(jsonPath("$.data.storageByTenant['tenant-1']").value(5368709120L))
                .andExpect(jsonPath("$.data.storageByTenant['tenant-2']").value(2684354560L))
                .andExpect(jsonPath("$.data.statisticsTime").exists());
    }
    
    @Test
    void testGetStatisticsEmpty() throws Exception {
        StorageStatistics statistics = StorageStatistics.builder()
                .totalFiles(0L).totalStorageBytes(0L).publicFiles(0L).privateFiles(0L)
                .filesByType(Collections.emptyMap()).storageByTenant(Collections.emptyMap())
                .statisticsTime(LocalDateTime.now()).build();
        when(fileManagementService.getStorageStatistics()).thenReturn(statistics);
        mockMvc.perform(get("/api/v1/admin/files/statistics")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalFiles").value(0))
                .andExpect(jsonPath("$.data.totalStorageBytes").value(0))
                .andExpect(jsonPath("$.data.publicFiles").value(0))
                .andExpect(jsonPath("$.data.privateFiles").value(0));
    }
    
    @Test
    void testGetStatisticsByTenant() throws Exception {
        TenantStorageStats stats1 = TenantStorageStats.builder()
                .tenantId("tenant-1").tenantName("Tenant 1")
                .fileCount(100L).storageBytes(5368709120L)
                .maxStorageBytes(10737418240L).maxFileCount(10000L)
                .storageUsagePercent(50.0).fileCountUsagePercent(1.0).build();
        TenantStorageStats stats2 = TenantStorageStats.builder()
                .tenantId("tenant-2").tenantName("Tenant 2")
                .fileCount(50L).storageBytes(2684354560L)
                .maxStorageBytes(5368709120L).maxFileCount(5000L)
                .storageUsagePercent(50.0).fileCountUsagePercent(1.0).build();
        Map<String, TenantStorageStats> statistics = new HashMap<>();
        statistics.put("tenant-1", stats1);
        statistics.put("tenant-2", stats2);
        when(fileManagementService.getStorageStatisticsByTenant()).thenReturn(statistics);
        mockMvc.perform(get("/api/v1/admin/files/statistics/by-tenant")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['tenant-1'].tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.data['tenant-1'].tenantName").value("Tenant 1"))
                .andExpect(jsonPath("$.data['tenant-1'].fileCount").value(100))
                .andExpect(jsonPath("$.data['tenant-1'].storageBytes").value(5368709120L))
                .andExpect(jsonPath("$.data['tenant-1'].storageUsagePercent").value(50.0))
                .andExpect(jsonPath("$.data['tenant-2'].tenantId").value("tenant-2"))
                .andExpect(jsonPath("$.data['tenant-2'].tenantName").value("Tenant 2"))
                .andExpect(jsonPath("$.data['tenant-2'].fileCount").value(50))
                .andExpect(jsonPath("$.data['tenant-2'].storageBytes").value(2684354560L));
    }
    
    @Test
    void testGetStatisticsByTenantEmpty() throws Exception {
        when(fileManagementService.getStorageStatisticsByTenant()).thenReturn(Collections.emptyMap());
        mockMvc.perform(get("/api/v1/admin/files/statistics/by-tenant")
                        .contentType(MediaType.APPLICATION_JSON).header("X-App-Id", "test-app"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }
    
    private FileRecord createFileRecord(String id, String appId, String userId, AccessLevel accessLevel) {
        return FileRecord.builder()
                .id(id).appId(appId).userId(userId)
                .storageObjectId("storage-" + id)
                .originalFilename("test-file.txt")
                .storagePath(appId + "/2026/01/21/" + userId + "/files/" + id + ".txt")
                .fileSize(1024L).contentType("text/plain")
                .fileHash("hash-" + id).hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED).accessLevel(accessLevel)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
