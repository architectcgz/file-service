package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MultipartUploadProgressQueryService {

    private final MultipartUploadTaskValidator multipartUploadTaskValidator;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadPartStateQueryService uploadPartStateQueryService;

    public UploadProgressResponse getProgress(String appId, String taskId, String userId) {
        var task = uploadTaskQueryService.getById(taskId);

        multipartUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK);
        int completedPartsCount = uploadPartStateQueryService.countCompletedParts(taskId);
        long uploadedBytes = Math.min((long) completedPartsCount * task.getChunkSize(), task.getFileSize());
        int percentage = (int) ((uploadedBytes * 100) / task.getFileSize());

        return UploadProgressResponse.builder()
                .taskId(task.getId())
                .totalParts(task.getTotalParts())
                .completedParts(completedPartsCount)
                .uploadedBytes(uploadedBytes)
                .totalBytes(task.getFileSize())
                .percentage(percentage)
                .build();
    }
}
