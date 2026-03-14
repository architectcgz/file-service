package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.BatchDeleteResult;
import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.application.dto.StorageStatistics;
import com.architectcgz.file.application.dto.TenantStorageStats;
import com.architectcgz.file.application.service.filemanagement.audit.FileManagementAuditService;
import com.architectcgz.file.application.service.filemanagement.command.FileAdminBatchDeleteCommandService;
import com.architectcgz.file.application.service.filemanagement.command.FileAdminDeleteCommandService;
import com.architectcgz.file.application.service.filemanagement.deletion.FileManagementDeletionService;
import com.architectcgz.file.application.service.filemanagement.query.FileManagementRecordQueryService;
import com.architectcgz.file.application.service.filemanagement.query.FileManagementQueryService;
import com.architectcgz.file.application.service.filemanagement.query.FileManagementStatisticsQueryService;
import com.architectcgz.file.application.service.filemanagement.validator.FileManagementAdminValidator;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文件管理应用层门面。
 *
 * 为管理端接口收口文件列表、详情、删除和统计入口，
 * 具体用例拆分到 command/query service。
 */
@Service
public class FileManagementService {

    private final FileManagementQueryService fileManagementQueryService;
    private final FileAdminDeleteCommandService fileAdminDeleteCommandService;
    private final FileAdminBatchDeleteCommandService fileAdminBatchDeleteCommandService;

    @Autowired
    public FileManagementService(FileManagementQueryService fileManagementQueryService,
                                 FileAdminDeleteCommandService fileAdminDeleteCommandService,
                                 FileAdminBatchDeleteCommandService fileAdminBatchDeleteCommandService) {
        this.fileManagementQueryService = fileManagementQueryService;
        this.fileAdminDeleteCommandService = fileAdminDeleteCommandService;
        this.fileAdminBatchDeleteCommandService = fileAdminBatchDeleteCommandService;
    }

    FileManagementService(FileRecordRepository fileRecordRepository,
                          TenantRepository tenantRepository,
                          TenantUsageRepository tenantUsageRepository,
                          StorageService storageService,
                          AuditLogService auditLogService,
                          FileUrlCacheManager fileUrlCacheManager,
                          FileDeleteTransactionHelper deleteTransactionHelper) {
        this(
                buildLegacyQueryService(fileRecordRepository, tenantRepository, tenantUsageRepository),
                buildLegacyDeleteCommandService(fileRecordRepository, storageService, auditLogService,
                        fileUrlCacheManager, deleteTransactionHelper),
                buildLegacyBatchDeleteCommandService(fileRecordRepository, storageService, auditLogService,
                        fileUrlCacheManager, deleteTransactionHelper)
        );
    }

    public PageResponse<FileRecord> listFiles(FileQuery query) {
        return fileManagementQueryService.listFiles(query);
    }

    public FileRecord getFileDetail(String fileId) {
        return fileManagementQueryService.getFileDetail(fileId);
    }

    public void deleteFile(String fileId, String adminUserId) {
        fileAdminDeleteCommandService.deleteFile(fileId, adminUserId);
    }

    public BatchDeleteResult batchDeleteFiles(List<String> fileIds, String adminUserId) {
        return fileAdminBatchDeleteCommandService.batchDeleteFiles(fileIds, adminUserId);
    }

    public StorageStatistics getStorageStatistics() {
        return fileManagementQueryService.getStorageStatistics();
    }

    public Map<String, TenantStorageStats> getStorageStatisticsByTenant() {
        return fileManagementQueryService.getStorageStatisticsByTenant();
    }

    private static FileManagementQueryService buildLegacyQueryService(FileRecordRepository fileRecordRepository,
                                                                      TenantRepository tenantRepository,
                                                                      TenantUsageRepository tenantUsageRepository) {
        return new FileManagementQueryService(
                new FileManagementRecordQueryService(fileRecordRepository),
                new FileManagementStatisticsQueryService(fileRecordRepository, tenantRepository, tenantUsageRepository)
        );
    }

    private static FileAdminDeleteCommandService buildLegacyDeleteCommandService(FileRecordRepository fileRecordRepository,
                                                                                 StorageService storageService,
                                                                                 AuditLogService auditLogService,
                                                                                 FileUrlCacheManager fileUrlCacheManager,
                                                                                 FileDeleteTransactionHelper deleteTransactionHelper) {
        return new FileAdminDeleteCommandService(
                new FileManagementAdminValidator(),
                new FileManagementRecordQueryService(fileRecordRepository),
                new FileManagementDeletionService(storageService, fileUrlCacheManager, deleteTransactionHelper),
                new FileManagementAuditService(auditLogService)
        );
    }

    private static FileAdminBatchDeleteCommandService buildLegacyBatchDeleteCommandService(FileRecordRepository fileRecordRepository,
                                                                                           StorageService storageService,
                                                                                           AuditLogService auditLogService,
                                                                                           FileUrlCacheManager fileUrlCacheManager,
                                                                                           FileDeleteTransactionHelper deleteTransactionHelper) {
        FileManagementAdminValidator adminValidator = new FileManagementAdminValidator();
        FileManagementAuditService auditService = new FileManagementAuditService(auditLogService);
        FileAdminDeleteCommandService deleteCommandService = new FileAdminDeleteCommandService(
                adminValidator,
                new FileManagementRecordQueryService(fileRecordRepository),
                new FileManagementDeletionService(storageService, fileUrlCacheManager, deleteTransactionHelper),
                auditService
        );
        return new FileAdminBatchDeleteCommandService(adminValidator, auditService, deleteCommandService);
    }
}
