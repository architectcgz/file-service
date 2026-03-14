package com.architectcgz.file.application.service.filedeletion;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeleteTransactionSupport {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return storageObjectRepository.findById(storageObjectId)
                .filter(StorageObject::isLastReference);
    }

    public void markFileDeleted(String fileRecordId) {
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);
    }

    public void deleteFileRecordOrThrow(String fileId) {
        boolean deleted = fileRecordRepository.deleteById(fileId);
        if (!deleted) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_DELETE_FAILED,
                    String.format(FileServiceErrorMessages.FILE_DELETE_FAILED, fileId)
            );
        }
        log.debug("文件记录已删除: fileId={}", fileId);
    }

    public void decrementTenantUsage(String appId, Long fileSize) {
        tenantUsageRepository.decrementUsage(appId, fileSize);
        log.debug("租户用量已递减: appId={}, size={}", appId, fileSize);
    }

    public void decrementStorageReferenceCount(String storageObjectId) {
        storageObjectRepository.decrementReferenceCount(storageObjectId);
    }

    public void deleteStorageObjectIfReleasable(String storageObjectId) {
        Optional<StorageObject> updatedOpt = storageObjectRepository.findById(storageObjectId);
        if (updatedOpt.isPresent() && updatedOpt.get().canBeDeleted()) {
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
        }
    }

    public void commitStorageRelease(String storageObjectId) {
        decrementStorageReferenceCount(storageObjectId);
        deleteStorageObjectIfReleasable(storageObjectId);
    }

    public String resolveStorageObjectId(FileRecord fileRecord) {
        return fileRecord.getStorageObjectId();
    }
}
