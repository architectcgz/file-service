package com.architectcgz.file.application.service.fileaccess.transaction;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLevelTransactionSupport {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;

    public void updateAccessLevelOrThrow(String fileId, AccessLevel newLevel) {
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPDATE_ACCESS_LEVEL_FAILED,
                    String.format(FileServiceErrorMessages.UPDATE_ACCESS_LEVEL_FAILED, fileId)
            );
        }
        log.debug("Updated file access level without storage rebinding: fileId={}, newLevel={}", fileId, newLevel);
    }

    public void saveCopiedStorageObject(StorageObject copiedStorageObject) {
        storageObjectRepository.save(copiedStorageObject);
        log.debug("Saved copied storage object: newStorageObjectId={}, bucket={}",
                copiedStorageObject.getId(), copiedStorageObject.getBucketName());
    }

    public void updateStorageBindingOrThrow(String fileId, StorageObject copiedStorageObject, AccessLevel newLevel) {
        boolean rebound = fileRecordRepository.updateStorageBindingAndAccessLevel(
                fileId,
                copiedStorageObject.getId(),
                copiedStorageObject.getStoragePath(),
                newLevel
        );
        if (!rebound) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPDATE_STORAGE_BINDING_FAILED,
                    String.format(FileServiceErrorMessages.UPDATE_STORAGE_BINDING_FAILED, fileId)
            );
        }
    }

    public void decrementReferenceCountOrThrow(String sourceStorageObjectId) {
        boolean decremented = storageObjectRepository.decrementReferenceCount(sourceStorageObjectId);
        if (!decremented) {
            throw new BusinessException(
                    FileServiceErrorCodes.STORAGE_REFERENCE_DECREMENT_FAILED,
                    String.format(FileServiceErrorMessages.STORAGE_REFERENCE_DECREMENT_FAILED, sourceStorageObjectId)
            );
        }
    }
}
