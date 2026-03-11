package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件访问级别切换事务辅助类
 *
 * 负责在短事务内完成文件记录与存储对象绑定关系的切换，
 * 避免在数据库事务中执行 S3/MinIO 等远程 I/O。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLevelChangeTransactionHelper {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;

    @Transactional(rollbackFor = Exception.class)
    public void updateAccessLevelOnly(String fileId, AccessLevel newLevel) {
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException("更新文件访问级别失败: " + fileId);
        }
        log.debug("Updated file access level without storage rebinding: fileId={}, newLevel={}", fileId, newLevel);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebindToCopiedStorage(String fileId, String sourceStorageObjectId,
                                      StorageObject copiedStorageObject, AccessLevel newLevel) {
        storageObjectRepository.save(copiedStorageObject);
        log.debug("Saved copied storage object: newStorageObjectId={}, bucket={}",
                copiedStorageObject.getId(), copiedStorageObject.getBucketName());

        boolean rebound = fileRecordRepository.updateStorageBindingAndAccessLevel(
                fileId,
                copiedStorageObject.getId(),
                copiedStorageObject.getStoragePath(),
                newLevel
        );
        if (!rebound) {
            throw new BusinessException("更新文件存储绑定失败: " + fileId);
        }

        boolean decremented = storageObjectRepository.decrementReferenceCount(sourceStorageObjectId);
        if (!decremented) {
            throw new BusinessException("减少原存储对象引用计数失败: " + sourceStorageObjectId);
        }

        log.debug("Rebound file to copied storage: fileId={}, oldStorageObjectId={}, newStorageObjectId={}, newLevel={}",
                fileId, sourceStorageObjectId, copiedStorageObject.getId(), newLevel);
    }
}
