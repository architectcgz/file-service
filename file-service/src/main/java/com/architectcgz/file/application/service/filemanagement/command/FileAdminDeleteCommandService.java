package com.architectcgz.file.application.service.filemanagement.command;

import com.architectcgz.file.application.service.filemanagement.audit.FileManagementAuditService;
import com.architectcgz.file.application.service.filemanagement.deletion.FileManagementDeletionService;
import com.architectcgz.file.application.service.filemanagement.query.FileManagementRecordQueryService;
import com.architectcgz.file.application.service.filemanagement.validator.FileManagementAdminValidator;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAdminDeleteCommandService {

    private final FileManagementAdminValidator fileManagementAdminValidator;
    private final FileManagementRecordQueryService fileManagementRecordQueryService;
    private final FileManagementDeletionService fileManagementDeletionService;
    private final FileManagementAuditService fileManagementAuditService;

    public void deleteFile(String fileId, String adminUserId) {
        adminUserId = fileManagementAdminValidator.requireAdminUserId(adminUserId);
        log.info("管理员删除文件: fileId={}, adminUserId={}", fileId, adminUserId);

        FileRecord fileRecord = fileManagementRecordQueryService.findFileOrThrow(fileId);
        String storageObjectId = fileRecord.getStorageObjectId();
        Optional<StorageObject> storageObjectToDelete = fileManagementDeletionService.findStorageObjectIfLastReference(storageObjectId);
        boolean needDeleteS3 = storageObjectToDelete.isPresent();

        if (needDeleteS3) {
            StorageObject storageObject = storageObjectToDelete.get();
            log.info("引用计数将归零，先删除 S3 对象: storageObjectId={}, bucket={}, path={}",
                    storageObjectId, storageObject.getBucketName(), storageObject.getStoragePath());
            fileManagementDeletionService.deleteStorageObject(storageObject);
            log.info("S3 对象删除成功: path={}", storageObject.getStoragePath());
        }

        fileManagementDeletionService.commitAdminDelete(fileId, fileRecord);
        fileManagementDeletionService.evictFileUrlCache(fileId);
        fileManagementAuditService.recordDeleteFileAudit(fileId, fileRecord, adminUserId);

        log.info("文件删除完成: fileId={}, s3Deleted={}", fileId, needDeleteS3);
    }
}
