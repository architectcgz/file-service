package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.*;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.domain.model.*;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件管理服务
 * 提供文件查询、删除和统计功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileManagementService {
    
    private final FileRecordRepository fileRecordRepository;
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;
    private final FileDeleteTransactionHelper deleteTransactionHelper;
    
    /**
     * 查询文件列表
     * 
     * @param query 查询条件
     * @return 分页文件列表
     */
    public PageResponse<FileRecord> listFiles(FileQuery query) {
        log.debug("Listing files with query: {}", query);
        
        List<FileRecord> files = fileRecordRepository.findByQuery(query);
        long total = fileRecordRepository.countByQuery(query);
        
        return PageResponse.of(files, query.getPage(), query.getSize(), total);
    }
    
    /**
     * 获取文件详情
     * 
     * @param fileId 文件ID
     * @return 文件记录
     */
    public FileRecord getFileDetail(String fileId) {
        log.debug("Getting file detail for fileId: {}", fileId);
        
        return fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));
    }
    
    /**
     * 删除单个文件（管理员操作）
     * 操作顺序：先删 S3 -> 再更新数据库，S3 删除失败则中止，保证一致性
     *
     * @param fileId 文件ID
     * @param adminUserId 管理员用户ID
     */
    public void deleteFile(String fileId, String adminUserId) {
        log.info("Deleting file: {} by admin: {}", fileId, adminUserId);

        // 查询文件记录
        FileRecord fileRecord = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));

        // 1. 先从存储系统删除文件，失败则整个操作中止
        storageService.delete(fileRecord.getStoragePath());
        log.debug("Deleted file from storage: {}", fileRecord.getStoragePath());

        // 2. S3 删除成功后，通过独立 Bean 执行短事务更新数据库
        deleteTransactionHelper.updateDatabaseAfterAdminDelete(fileId, fileRecord);

        // 3. 清除缓存（缓存操作不影响核心流程）
        clearCache(fileId);

        // 4. 记录审计日志
        recordDeleteFileAudit(fileId, fileRecord, adminUserId);

        log.info("Successfully deleted file: {}", fileId);
    }
    
    /**
     * 批量删除文件
     * 
     * @param fileIds 文件ID列表
     * @param adminUserId 管理员用户ID
     * @return 批量删除结果
     */
    public BatchDeleteResult batchDeleteFiles(List<String> fileIds, String adminUserId) {
        log.info("Batch deleting {} files by admin: {}", fileIds.size(), adminUserId);
        
        BatchDeleteResult result = BatchDeleteResult.builder()
                .totalRequested(fileIds.size())
                .successCount(0)
                .failureCount(0)
                .failures(new ArrayList<>())
                .build();
        
        for (String fileId : fileIds) {
            try {
                deleteFile(fileId, adminUserId);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("Failed to delete file in batch: {}", fileId, e);
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailures().add(
                        BatchDeleteResult.DeleteFailure.builder()
                                .fileId(fileId)
                                .reason(e.getMessage())
                                .build()
                );
            }
        }
        
        // 记录批量删除审计日志
        recordBatchDeleteAudit(fileIds, result, adminUserId);
        
        log.info("Batch delete completed: {} succeeded, {} failed", 
                result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * 获取存储统计
     * 
     * @return 存储统计信息
     */
    public StorageStatistics getStorageStatistics() {
        log.debug("Getting storage statistics");
        
        // 查询所有文件
        FileQuery query = new FileQuery();
        query.setSize(Integer.MAX_VALUE);
        List<FileRecord> allFiles = fileRecordRepository.findByQuery(query);
        
        // 统计总文件数和总存储空间
        long totalFiles = allFiles.size();
        long totalStorageBytes = allFiles.stream()
                .mapToLong(FileRecord::getFileSize)
                .sum();
        
        // 统计公开和私有文件数量
        long publicFiles = allFiles.stream()
                .filter(f -> f.getAccessLevel() == AccessLevel.PUBLIC)
                .count();
        long privateFiles = allFiles.stream()
                .filter(f -> f.getAccessLevel() == AccessLevel.PRIVATE)
                .count();
        
        // 统计按文件类型分布
        Map<String, Long> filesByType = allFiles.stream()
                .collect(Collectors.groupingBy(
                        FileRecord::getContentType,
                        Collectors.counting()
                ));
        
        // 统计按租户存储空间分布
        Map<String, Long> storageByTenant = allFiles.stream()
                .collect(Collectors.groupingBy(
                        FileRecord::getAppId,
                        Collectors.summingLong(FileRecord::getFileSize)
                ));
        
        return StorageStatistics.builder()
                .totalFiles(totalFiles)
                .totalStorageBytes(totalStorageBytes)
                .publicFiles(publicFiles)
                .privateFiles(privateFiles)
                .filesByType(filesByType)
                .storageByTenant(storageByTenant)
                .statisticsTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 按租户获取存储统计
     * 
     * @return 租户存储统计映射（tenantId -> TenantStorageStats）
     */
    public Map<String, TenantStorageStats> getStorageStatisticsByTenant() {
        log.debug("Getting storage statistics by tenant");
        
        Map<String, TenantStorageStats> statsMap = new HashMap<>();
        
        // 查询所有租户
        List<Tenant> tenants = tenantRepository.findAll();
        
        for (Tenant tenant : tenants) {
            // 查询租户使用统计
            Optional<TenantUsage> usageOpt = tenantUsageRepository.findById(tenant.getTenantId());
            
            if (usageOpt.isPresent()) {
                TenantUsage usage = usageOpt.get();
                
                // 计算使用百分比
                double storageUsagePercent = tenant.getMaxStorageBytes() > 0
                        ? (double) usage.getUsedStorageBytes() / tenant.getMaxStorageBytes() * 100
                        : 0;
                double fileCountUsagePercent = tenant.getMaxFileCount() > 0
                        ? (double) usage.getUsedFileCount() / tenant.getMaxFileCount() * 100
                        : 0;
                
                TenantStorageStats stats = TenantStorageStats.builder()
                        .tenantId(tenant.getTenantId())
                        .tenantName(tenant.getTenantName())
                        .fileCount(usage.getUsedFileCount())
                        .storageBytes(usage.getUsedStorageBytes())
                        .maxStorageBytes(tenant.getMaxStorageBytes())
                        .maxFileCount(tenant.getMaxFileCount())
                        .storageUsagePercent(storageUsagePercent)
                        .fileCountUsagePercent(fileCountUsagePercent)
                        .build();
                
                statsMap.put(tenant.getTenantId(), stats);
            }
        }
        
        return statsMap;
    }
    
    /**
     * 清除文件 URL 缓存
     * 
     * @param fileId 文件ID
     */
    private void clearCache(String fileId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        
        try {
            String cacheKey = com.architectcgz.file.infrastructure.cache.FileRedisKeys.fileUrl(fileId);
            Boolean deleted = redisTemplate.delete(cacheKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Cache cleared: fileId={}", fileId);
            } else {
                log.debug("Cache not found: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.warn("Failed to clear cache: fileId={}", fileId, e);
            // 缓存清除失败不影响业务流程
        }
    }
    
    /**
     * 记录删除文件的审计日志
     */
    private void recordDeleteFileAudit(String fileId, FileRecord fileRecord, String adminUserId) {
        Map<String, Object> details = new HashMap<>();
        details.put("fileName", fileRecord.getOriginalFilename());
        details.put("fileSize", fileRecord.getFileSize());
        details.put("contentType", fileRecord.getContentType());
        details.put("storagePath", fileRecord.getStoragePath());
        
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(adminUserId != null ? adminUserId : AdminContext.getAdminUser())
                .action(AuditAction.DELETE_FILE)
                .targetType(TargetType.FILE)
                .targetId(fileId)
                .tenantId(fileRecord.getAppId())
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
    
    /**
     * 记录批量删除文件的审计日志
     */
    private void recordBatchDeleteAudit(List<String> fileIds, BatchDeleteResult result, String adminUserId) {
        Map<String, Object> details = new HashMap<>();
        details.put("totalRequested", result.getTotalRequested());
        details.put("successCount", result.getSuccessCount());
        details.put("failureCount", result.getFailureCount());
        details.put("fileIds", fileIds);
        
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(adminUserId != null ? adminUserId : AdminContext.getAdminUser())
                .action(AuditAction.BATCH_DELETE_FILES)
                .targetType(TargetType.FILE)
                .targetId(String.format("batch_%d_files", fileIds.size()))
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();
        
        auditLogService.log(auditLog);
    }
}
