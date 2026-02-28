package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件删除事务辅助类
 * 将数据库更新操作封装为独立的短事务，供 UploadApplicationService 和 FileManagementService 调用。
 * 解决同类内部调用 @Transactional 不生效的问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeleteTransactionHelper {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final TenantUsageRepository tenantUsageRepository;

    /**
     * 用户删除文件后更新数据库（独立短事务）
     * 包括：软删除 FileRecord、减少引用计数、更新租户用量、清理零引用 StorageObject
     *
     * @param appId 应用ID
     * @param fileRecordId 文件记录ID
     * @param storageObjectId 存储对象ID
     * @param fileSize 文件大小（字节）
     * @param shouldDeleteStorageObject 是否需要删除 StorageObject 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDatabaseAfterUserDelete(String appId, String fileRecordId,
                                               String storageObjectId, Long fileSize,
                                               boolean shouldDeleteStorageObject) {
        // 软删除 FileRecord
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);

        // 更新租户使用统计
        tenantUsageRepository.decrementUsage(appId, fileSize);

        // 减少 StorageObject 引用计数
        storageObjectRepository.decrementReferenceCount(storageObjectId);

        // 如果 S3 对象已删除，同步删除 StorageObject 记录
        if (shouldDeleteStorageObject) {
            storageObjectRepository.deleteById(storageObjectId);
            log.info("StorageObject 记录已删除: storageObjectId={}", storageObjectId);
        }
    }

    /**
     * 管理员删除文件后更新数据库（独立短事务）
     * 包括：硬删除 FileRecord、更新租户用量
     *
     * @param fileId 文件ID
     * @param fileRecord 文件记录（用于获取 appId 和 fileSize）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDatabaseAfterAdminDelete(String fileId, FileRecord fileRecord) {
        // 硬删除 FileRecord
        boolean deleted = fileRecordRepository.deleteById(fileId);
        if (!deleted) {
            throw new RuntimeException("Failed to delete file record from database");
        }
        log.debug("Deleted file record from database: {}", fileId);

        // 更新租户使用统计
        tenantUsageRepository.decrementUsage(fileRecord.getAppId(), fileRecord.getFileSize());
        log.debug("Decremented tenant usage for tenant: {}, size: {}",
                fileRecord.getAppId(), fileRecord.getFileSize());
    }
}
