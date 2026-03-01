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
 *
 * 设计要点（S3 与数据库一致性）：
 * - 调用方应在调用本类方法之前完成 S3 删除，本类只负责事后更新数据库。
 * - 遵循"先删 S3，再短事务更新数据库"原则：S3 删除失败时整个操作中止，数据库保持不变，
 *   孤立文件可被定时任务追踪和清理；反之若先删库再删 S3，S3 删除失败则孤立文件无法追踪。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeleteTransactionHelper {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final TenantUsageRepository tenantUsageRepository;

    /**
     * 查询 StorageObject 并判断引用计数是否会在递减后归零
     * 供调用方在执行 S3 删除前预判是否需要删除 S3 对象。
     *
     * @param storageObjectId 存储对象ID
     * @return 如果引用计数为 1（递减后归零），返回 StorageObject；否则返回 empty
     */
    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return storageObjectRepository.findById(storageObjectId)
                .filter(StorageObject::isLastReference);
    }

    /**
     * 用户删除文件：S3 已删除后，用短事务原子更新数据库
     * 包括：软删除 FileRecord、减少引用计数、更新租户用量；
     * 若引用计数归零则同时删除 StorageObject 记录。
     *
     * 前置条件：调用方已完成 S3 对象删除（如需要）。
     *
     * @param appId           应用ID
     * @param fileRecordId    文件记录ID
     * @param storageObjectId 存储对象ID
     * @param fileSize        文件大小（字节）
     */
    @Transactional(rollbackFor = Exception.class)
    public void commitUserDelete(String appId, String fileRecordId,
                                 String storageObjectId, Long fileSize) {
        // 软删除 FileRecord
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);

        // 更新租户使用统计
        tenantUsageRepository.decrementUsage(appId, fileSize);

        // 原子递减 StorageObject 引用计数
        storageObjectRepository.decrementReferenceCount(storageObjectId);

        // 查询递减后的引用计数，归零则删除 StorageObject 记录
        Optional<StorageObject> updatedOpt = storageObjectRepository.findById(storageObjectId);
        if (updatedOpt.isPresent() && updatedOpt.get().canBeDeleted()) {
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
        }
    }

    /**
     * 管理员删除文件：S3 已删除后，用短事务原子更新数据库
     * 包括：硬删除 FileRecord、递减引用计数、更新租户用量；
     * 若引用计数归零则同时删除 StorageObject 记录。
     *
     * 前置条件：调用方已完成 S3 对象删除（如需要）。
     *
     * @param fileId     文件ID
     * @param fileRecord 文件记录（用于获取 appId、fileSize、storageObjectId）
     */
    @Transactional(rollbackFor = Exception.class)
    public void commitAdminDelete(String fileId, FileRecord fileRecord) {
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
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
        }
    }
}
