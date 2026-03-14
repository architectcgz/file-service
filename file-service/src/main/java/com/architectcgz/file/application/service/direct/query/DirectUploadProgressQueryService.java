package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.assembler.DirectUploadPartResponseAssembler;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectUploadProgressQueryService {

    private final DirectUploadTaskValidator directUploadTaskValidator;
    private final DirectUploadStorageService directUploadStorageService;
    private final DirectUploadPartResponseAssembler directUploadPartResponseAssembler;
    private final UploadTaskQueryService uploadTaskQueryService;

    public DirectUploadProgressResponse getUploadProgress(String appId, String taskId, String userId) {
        var task = uploadTaskQueryService.getById(taskId);

        directUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK);
        directUploadTaskValidator.ensureTaskUploadingAndNotExpired(task);

        var uploadedPartInfos = directUploadStorageService.fetchUploadedPartInfos(
                task,
                directUploadStorageService.resolveUploadBucketName()
        );
        int completedParts = uploadedPartInfos.size();
        long uploadedBytes = Math.min((long) completedParts * task.getChunkSize(), task.getFileSize());
        int percentage = (int) ((uploadedBytes * 100) / task.getFileSize());

        return DirectUploadProgressResponse.builder()
                .taskId(task.getId())
                .totalParts(task.getTotalParts())
                .completedParts(completedParts)
                .uploadedBytes(uploadedBytes)
                .totalBytes(task.getFileSize())
                .percentage(percentage)
                .completedPartNumbers(directUploadPartResponseAssembler.extractCompletedPartNumbers(uploadedPartInfos))
                .completedPartInfos(directUploadPartResponseAssembler.toResponsePartInfos(uploadedPartInfos))
                .build();
    }
}
