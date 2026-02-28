package com.architectcgz.file.application.service;

import com.architectcgz.file.common.constants.FileErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
     * 用户删除文件：在事务内原子地递减引用计数并判断是否归零
     * 包括：软删除 FileRecord、减少引用计数、更新租户用量、清理零引用 StorageObject
     *
     * 返回值表示是否需要在事务提交后删除 S3 对象及对应的存储路径。
     * 调用方应在事务提交后根据返回值决定是否执行 S3 删除。
     *
     * @param appId 应用ID
     * @param fileRecordId 文件记录ID
     * @param storageObjectId 存储对象ID
     * @param fileSize 文件大小（字节）
     * @return 需要删除的 S3 存储路径，如果不需要删除则返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public String updateDatabaseAfterUserDelete(String appId, String fileRecordId,
                                                 String storageObjectId, Long fileSize) {
        // 软删除 FileRecord
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);

        // 更新租户使用统计
        tenantUsageRepository.decrementUsage(appId, fileSize);

        // 原子递减 StorageObject 引用计数
        storageObjectRepository.decrementReferenceCount(storageObjectId);

        // 在同一事务内查询更新后的引用计数，判断是否归零
        Optional<StorageObject> updatedOpt = storageObjectRepository.findById(storageObjectId);
        if (updatedOpt.isPresent() && updatedOpt.get().canBeDeleted()) {
            // 引用计数归零，删除 StorageObject 记录，返回存储路径供调用方删除 S3
            String storagePath = updatedOpt.get().getStoragePath();
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
            return storagePath;
        }

        return null;
    }

    /**
     * 管理员删除文件后更新数据库（独立短事务）
     * 包括：硬删除 FileRecord、递减引用计数、更新租户用量、清理零引用 StorageObject
     *
     * 返回值表示是否需要在事务提交后删除 S3 对象及对应的存储路径。
     *
     * @param fileId 文件ID
     * @param fileRecord 文件记录（用于获取 appId、fileSize、storageObjectId）
     * @return 需要删除的 S3 存储路径，如果不需要删除则返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public String updateDatabaseAfterAdminDelete(String fileId, FileRecord fileRecord) {
        // 硬删除 FileRecord
        boolean deleted = fileRecordRepository.deleteById(fileId);
        if (!deleted) {
            throw new BusinessException(FileErrorMessages.FILE_RECORD_DELETE_FAILED);
        }
        log.debug("文件记录已删除: fileId={}", fileId);

        // 更新租户使用统计
        tenantUsageRepository.decrementUsage(fileRecord.getAppId(), fileRecord.getFileSize());
        log.debug("租户用量已递减: appId={}, size={}", fileRecord.getAppId(), fileRecord.getFileSize());

        // 递减 StorageObject 引用计数并判断是否归零
        String storageObjectId = fileRecord.getStorageObjectId();
        storageObjectRepository.decrementReferenceCount(storageObjectId);

        Optional<StorageObject> updatedOpt = storageObjectRepository.findById(storageObjectId);
        if (updatedOpt.isPresent() && updatedOpt.get().canBeDeleted()) {
            String storagePath = updatedOpt.get().getStoragePath();
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
            return storagePath;
        }

        return null;
    }
}
