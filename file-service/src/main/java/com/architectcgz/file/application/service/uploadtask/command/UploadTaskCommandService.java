package com.architectcgz.file.application.service.uploadtask.command;

import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 上传任务写操作服务。
 */
@Service
@RequiredArgsConstructor
public class UploadTaskCommandService {

    private final UploadTaskRepository uploadTaskRepository;
    private final UploadTaskFactory uploadTaskFactory;

    public UploadTask createUploadingTask(String appId,
                                          String userId,
                                          String fileName,
                                          Long fileSize,
                                          String fileHash,
                                          String contentType,
                                          String storagePath,
                                          String uploadId,
                                          int totalParts,
                                          int chunkSize,
                                          int taskExpireHours) {
        UploadTask task = uploadTaskFactory.createUploadingTask(
                appId,
                userId,
                fileName,
                fileSize,
                fileHash,
                contentType,
                storagePath,
                uploadId,
                totalParts,
                chunkSize,
                taskExpireHours
        );
        uploadTaskRepository.save(task);
        return task;
    }

    public void markExpired(String taskId) {
        uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.EXPIRED);
    }

    public void markAborted(String taskId) {
        uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.ABORTED);
    }
}
