package com.architectcgz.file.application.service.fileaccess.command;

import com.architectcgz.file.application.service.AccessLevelChangeTransactionHelper;
import com.architectcgz.file.application.service.fileaccess.factory.FileAccessObjectFactory;
import com.architectcgz.file.application.service.fileaccess.query.FileAccessRecordQueryService;
import com.architectcgz.file.application.service.fileaccess.storage.FileAccessStorageService;
import com.architectcgz.file.application.service.fileaccess.validator.FileAccessValidator;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessLevelCommandService {

    private final FileAccessRecordQueryService fileAccessRecordQueryService;
    private final FileAccessValidator fileAccessValidator;
    private final FileAccessObjectFactory fileAccessObjectFactory;
    private final FileAccessStorageService fileAccessStorageService;
    private final AccessLevelChangeTransactionHelper accessLevelChangeTransactionHelper;

    @Transactional
    public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
        var file = fileAccessRecordQueryService.findFileOrThrow(fileId);
        fileAccessValidator.validateFileAccess(file, fileId, requestUserId, appId);
        fileAccessValidator.validateOwnerCanUpdateAccessLevel(file, fileId, requestUserId);

        if (file.getAccessLevel() == newLevel) {
            log.debug("Access level unchanged, skip update: fileId={}, level={}", fileId, newLevel);
            return;
        }

        var storageObjectOpt = fileAccessRecordQueryService.resolveStorageObject(file);
        String targetBucketName = fileAccessStorageService.resolveTargetBucketName(newLevel);
        boolean sourceObjectShouldDelete = false;
        String sourceBucketName = null;
        String sourcePath = file.getStoragePath();

        if (storageObjectOpt.isEmpty()) {
            accessLevelChangeTransactionHelper.updateAccessLevelOnly(fileId, newLevel);
        } else {
            StorageObject storageObject = storageObjectOpt.get();
            sourceBucketName = storageObject.getBucketName();
            sourcePath = storageObject.getStoragePath();
            if (fileAccessStorageService.isBucketMatchTarget(sourceBucketName, targetBucketName)) {
                accessLevelChangeTransactionHelper.updateAccessLevelOnly(fileId, newLevel);
            } else {
                fileAccessStorageService.copy(sourceBucketName, sourcePath, targetBucketName, sourcePath);
                StorageObject copiedStorageObject = fileAccessObjectFactory.buildCopiedStorageObject(
                        storageObject,
                        targetBucketName
                );
                try {
                    accessLevelChangeTransactionHelper.rebindToCopiedStorage(
                            fileId, storageObject.getId(), copiedStorageObject, newLevel
                    );
                } catch (RuntimeException ex) {
                    fileAccessStorageService.deleteQuietly(targetBucketName, sourcePath);
                    throw ex;
                }
                sourceObjectShouldDelete = storageObject.isLastReference();
            }
        }

        log.info("File access level updated: fileId={}, oldLevel={}, newLevel={}, userId={}",
                fileId, file.getAccessLevel(), newLevel, requestUserId);
        fileAccessStorageService.registerAfterCommitCleanup(
                fileId,
                sourceObjectShouldDelete,
                sourceBucketName,
                sourcePath
        );
    }
}
