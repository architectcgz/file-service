package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.multipart.factory.MultipartUploadObjectFactory;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.command.UploadPartSyncCommandService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartCompletionQueryService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadCompleteCommandService {

    private final MultipartUploadTaskValidator multipartUploadTaskValidator;
    private final MultipartUploadStorageService multipartUploadStorageService;
    private final MultipartUploadObjectFactory multipartUploadObjectFactory;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadPartStateQueryService uploadPartStateQueryService;
    private final UploadPartSyncCommandService uploadPartSyncCommandService;
    private final UploadPartCompletionQueryService uploadPartCompletionQueryService;
    private final StorageObjectRepository storageObjectRepository;
    private final UploadTransactionHelper uploadTransactionHelper;

    public String completeUpload(String appId, String taskId, String userId) {
        log.info("Completing multipart upload for task: {}", taskId);
        String targetBucketName = multipartUploadStorageService.resolveUploadBucketName();

        var task = uploadTaskQueryService.getById(taskId);

        multipartUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        if (task.getStatus() != com.architectcgz.file.domain.model.UploadTaskStatus.UPLOADING) {
            throw new BusinessException(
                    FileServiceErrorCodes.TASK_STATUS_INVALID,
                    String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus())
            );
        }

        int completedCount = uploadPartStateQueryService.countCompletedParts(taskId);
        if (completedCount != task.getTotalParts()) {
            throw new BusinessException(
                    FileServiceErrorCodes.PARTS_INCOMPLETE,
                    String.format(FileServiceErrorMessages.PARTS_INCOMPLETE, completedCount, task.getTotalParts())
            );
        }

        uploadPartSyncCommandService.syncAllParts(taskId, List.of());
        List<CompletedPart> completedParts = uploadPartCompletionQueryService.loadPersistedCompletedParts(taskId);

        multipartUploadStorageService.completeMultipartUpload(
                task.getStoragePath(),
                task.getUploadId(),
                completedParts,
                targetBucketName
        );

        String fileHash = multipartUploadTaskValidator.requireFileHash(task);
        Optional<com.architectcgz.file.domain.model.StorageObject> existingStorageObject =
                storageObjectRepository.findByFileHashAndBucket(task.getAppId(), fileHash, targetBucketName);

        if (existingStorageObject.isPresent()) {
            var storageObject = existingStorageObject.get();
            var fileRecord = multipartUploadObjectFactory.buildFileRecord(
                    task, userId, storageObject.getId(), fileHash,
                    storageObject.getStoragePath(), storageObject.getFileSize(), storageObject.getContentType()
            );

            try {
                uploadTransactionHelper.saveCompletedInstantUpload(task, storageObject.getId(), fileRecord);
            } catch (Exception dbEx) {
                multipartUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
                throw dbEx;
            }

            multipartUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            return fileRecord.getId();
        }

        var storageObject = multipartUploadObjectFactory.buildStorageObject(task, fileHash, targetBucketName);
        var fileRecord = multipartUploadObjectFactory.buildFileRecord(
                task, userId, storageObject.getId(), fileHash,
                task.getStoragePath(), task.getFileSize(), task.getContentType()
        );

        try {
            uploadTransactionHelper.saveCompletedUpload(task, storageObject, fileRecord);
        } catch (Exception dbEx) {
            multipartUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            throw dbEx;
        }

        return fileRecord.getId();
    }
}
