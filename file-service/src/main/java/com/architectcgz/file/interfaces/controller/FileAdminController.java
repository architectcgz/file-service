package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.*;
import com.architectcgz.file.application.service.FileManagementService;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件管理控制器
 * 提供管理员文件管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
public class FileAdminController {
    
    private final FileManagementService fileManagementService;
    
    /**
     * 查询文件列表
     * 
     * @param tenantId 租户ID（可选）
     * @param userId 用户ID（可选）
     * @param contentType 内容类型（可选）
     * @param accessLevel 访问级别（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param minSize 最小文件大小（可选）
     * @param maxSize 最大文件大小（可选）
     * @param page 页码（默认0）
     * @param size 每页大小（默认20）
     * @param sortBy 排序字段（默认createdAt）
     * @param sortOrder 排序方向（默认desc）
     * @return 分页文件列表
     */
    @GetMapping
    public ApiResponse<PageResponse<FileRecord>> listFiles(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) AccessLevel accessLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        log.info("Admin listing files with filters - tenantId: {}, userId: {}, contentType: {}, accessLevel: {}", 
                tenantId, userId, contentType, accessLevel);
        
        FileQuery query = new FileQuery();
        query.setTenantId(tenantId);
        query.setUserId(userId);
        query.setContentType(contentType);
        query.setAccessLevel(accessLevel);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setMinSize(minSize);
        query.setMaxSize(maxSize);
        query.setPage(page);
        query.setSize(size);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        
        PageResponse<FileRecord> result = fileManagementService.listFiles(query);
        return ApiResponse.success(result);
    }
    
    /**
     * 查询文件详情
     * 
     * @param fileId 文件ID
     * @return 文件详情
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileRecord> getFileDetail(@PathVariable String fileId) {
        log.info("Admin getting file detail for fileId: {}", fileId);
        
        FileRecord fileRecord = fileManagementService.getFileDetail(fileId);
        return ApiResponse.success(fileRecord);
    }
    
    /**
     * 删除文件
     * 
     * @param fileId 文件ID
     * @return 删除结果
     */
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String fileId) {
        log.info("Admin deleting file: {}", fileId);
        
        String adminUserId = resolveAdminUserId();
        
        fileManagementService.deleteFile(fileId, adminUserId);
        return ApiResponse.success(null);
    }
    
    /**
     * 批量删除文件
     * 
     * @param request 批量删除请求
     * @return 批量删除结果
     */
    @PostMapping("/batch-delete")
    public ApiResponse<BatchDeleteResult> batchDeleteFiles(@RequestBody BatchDeleteRequest request) {
        log.info("Admin batch deleting {} files", request.getFileIds().size());
        
        String adminUserId = resolveAdminUserId();
        
        BatchDeleteResult result = fileManagementService.batchDeleteFiles(request.getFileIds(), adminUserId);
        return ApiResponse.success(result);
    }

    private String resolveAdminUserId() {
        String adminUserId = AdminContext.getAdminUser();
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new AccessDeniedException("未获取到管理员身份");
        }
        return adminUserId;
    }
    
    /**
     * 获取存储统计
     * 
     * @return 存储统计信息
     */
    @GetMapping("/statistics")
    public ApiResponse<StorageStatistics> getStatistics() {
        log.info("Admin getting storage statistics");
        
        StorageStatistics statistics = fileManagementService.getStorageStatistics();
        return ApiResponse.success(statistics);
    }
    
    /**
     * 按租户获取存储统计
     * 
     * @return 租户存储统计映射
     */
    @GetMapping("/statistics/by-tenant")
    public ApiResponse<Map<String, TenantStorageStats>> getStatisticsByTenant() {
        log.info("Admin getting storage statistics by tenant");
        
        Map<String, TenantStorageStats> statistics = fileManagementService.getStorageStatisticsByTenant();
        return ApiResponse.success(statistics);
    }
}
