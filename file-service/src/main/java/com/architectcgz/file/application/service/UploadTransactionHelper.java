package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图片上传事务辅助类
 *
 * 将数据库写入操作封装为独立的短事务方法，与 S3 上传等 I/O 操作解耦。
 * 调用方（UploadApplicationService）负责在事务外完成 S3 上传，
 * 若事务失败则由调用方执行 S3 补偿清理。
 *
 * 参考项目分层规范，放在 application/service 包下，作为应用层内部协作组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTransactionHelper {

    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final UploadTaskRepository uploadTaskRepository;

    /**
     * 新文件上传的数据库写入事务
     * 保存 StorageObject、FileRecord，并更新租户用量统计。
     * 三步操作在同一事务内，任意一步失败均回滚。
     *
     * @param storageObject 待保存的存储对象
     * @param fileRecord    待保存的文件记录
     * @param fileSize      文件大小（字节），用于更新租户用量
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveNewUpload(StorageObject storageObject, FileRecord fileRecord, long fileSize) {
        storageObjectRepository.save(storageObject);
        log.debug("StorageObject saved: id={}, path={}", storageObject.getId(), storageObject.getStoragePath());

        fileRecordRepository.save(fileRecord);
        log.debug("FileRecord saved: id={}, storageObjectId={}", fileRecord.getId(), fileRecord.getStorageObjectId());

        tenantUsageRepository.incrementUsage(fileRecord.getAppId(), fileSize);
        log.debug("Tenant usage incremented: appId={}, delta={}", fileRecord.getAppId(), fileSize);
    }

    /**
     * 秒传（去重命中）的数据库写入事务
     * 增加已有 StorageObject 的引用计数、保存新 FileRecord，并更新租户用量统计。
     * 三步操作在同一事务内，任意一步失败均回滚。
     *
     * @param storageObjectId 已存在的存储对象 ID
     * @param fileRecord      待保存的文件记录
     * @param fileSize        文件大小（字节），用于更新租户用量
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveInstantUpload(String storageObjectId, FileRecord fileRecord, long fileSize) {
        storageObjectRepository.incrementReferenceCount(storageObjectId);
        log.debug("StorageObject reference count incremented: id={}", storageObjectId);

        fileRecordRepository.save(fileRecord);
        log.debug("FileRecord saved (instant upload): id={}, storageObjectId={}", fileRecord.getId(), storageObjectId);

        tenantUsageRepository.incrementUsage(fileRecord.getAppId(), fileSize);
        log.debug("Tenant usage incremented: appId={}, delta={}", fileRecord.getAppId(), fileSize);
    }

    /**
     * 分片/直传完成后的数据库写入事务。
     * 保存 StorageObject、FileRecord、更新租户用量，并将 UploadTask 标记为完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCompletedUpload(UploadTask task, StorageObject storageObject, FileRecord fileRecord) {
        storageObjectRepository.save(storageObject);
        log.debug("StorageObject saved for completed upload: id={}, path={}",
                storageObject.getId(), storageObject.getStoragePath());

        fileRecordRepository.save(fileRecord);
        log.debug("FileRecord saved for completed upload: id={}, storageObjectId={}",
                fileRecord.getId(), fileRecord.getStorageObjectId());

        tenantUsageRepository.incrementUsage(task.getAppId(), fileRecord.getFileSize());
        log.debug("Tenant usage incremented for completed upload: appId={}, delta={}",
                task.getAppId(), fileRecord.getFileSize());

        uploadTaskRepository.updateStatus(task.getId(), UploadTaskStatus.COMPLETED);
        log.debug("UploadTask marked completed in transaction: taskId={}", task.getId());
    }
}
