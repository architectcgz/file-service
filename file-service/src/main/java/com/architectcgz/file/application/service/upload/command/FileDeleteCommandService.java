package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileDeleteTransactionHelper;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeleteCommandService {

    private final StorageService storageService;
    private final FileRecordRepository fileRecordRepository;
    private final FileUrlCacheManager fileUrlCacheManager;
    private final FileDeleteTransactionHelper deleteTransactionHelper;

    public void deleteFile(String appId, String fileRecordId, String userId) {
        FileRecord fileRecord = fileRecordRepository.findById(fileRecordId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileRecordId));

        if (!fileRecord.belongsToApp(appId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.FILE_NOT_BELONG_TO_APP);
        }
        if (!fileRecord.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_DELETE_FILE);
        }

        String storageObjectId = fileRecord.getStorageObjectId();
        Optional<StorageObject> storageObjectToDelete = deleteTransactionHelper
                .findStorageObjectIfLastReference(storageObjectId);
        boolean needDeleteStorage = storageObjectToDelete.isPresent();

        if (needDeleteStorage) {
            StorageObject storageObject = storageObjectToDelete.get();
            if (storageObject.getStoragePath() != null) {
                log.info("引用计数将归零，先删除存储对象: storageObjectId={}, bucket={}, path={}",
                        storageObjectId, storageObject.getBucketName(), storageObject.getStoragePath());
                storageService.delete(storageObject.getBucketName(), storageObject.getStoragePath());
                log.info("存储对象删除成功: path={}", storageObject.getStoragePath());
            }
        }

        deleteTransactionHelper.commitUserDelete(
                appId, fileRecordId, storageObjectId, fileRecord.getFileSize());
        fileUrlCacheManager.evict(fileRecordId);

        log.info("文件已删除: fileRecordId={}, userId={}, storageDeleted={}",
                fileRecordId, userId, needDeleteStorage);
    }
}
