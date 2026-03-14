package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadAbortCommandService {

    private final MultipartUploadTaskValidator multipartUploadTaskValidator;
    private final MultipartUploadStorageService multipartUploadStorageService;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadTaskCommandService uploadTaskCommandService;

    @Transactional
    public void abortUpload(String appId, String taskId, String userId) {
        log.info("Aborting multipart upload for task: {}", taskId);

        var task = uploadTaskQueryService.getById(taskId);

        multipartUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            log.warn("Task {} is not in UPLOADING status: {}", taskId, task.getStatus());
            return;
        }

        try {
            multipartUploadStorageService.abortMultipartUpload(
                    task.getStoragePath(),
                    task.getUploadId(),
                    multipartUploadStorageService.resolveUploadBucketName()
            );
        } catch (Exception e) {
            log.error("Failed to abort S3 multipart upload for task: {}", taskId, e);
        }

        uploadTaskCommandService.markAborted(taskId);
    }
}
