package com.architectcgz.file.application.service.uploadtask.factory;

import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 上传任务对象工厂。
 */
@Component
public class UploadTaskFactory {

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
        LocalDateTime now = LocalDateTime.now();

        UploadTask task = new UploadTask();
        task.setId(UuidCreator.getTimeOrderedEpoch().toString());
        task.setAppId(appId);
        task.setUserId(userId);
        task.setFileName(fileName);
        task.setFileSize(fileSize);
        task.setFileHash(fileHash);
        task.setContentType(contentType);
        task.setStoragePath(storagePath);
        task.setUploadId(uploadId);
        task.setTotalParts(totalParts);
        task.setChunkSize(chunkSize);
        task.setStatus(UploadTaskStatus.UPLOADING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setExpiresAt(now.plusHours(taskExpireHours));
        return task;
    }
}
