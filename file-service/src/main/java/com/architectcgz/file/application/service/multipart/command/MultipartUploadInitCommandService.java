package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.multipart.factory.MultipartUploadObjectFactory;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadInitCommandService {

    private final MultipartUploadStorageService multipartUploadStorageService;
    private final MultipartUploadObjectFactory multipartUploadObjectFactory;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadTaskCommandService uploadTaskCommandService;
    private final UploadPartStateQueryService uploadPartStateQueryService;
    private final MultipartProperties multipartProperties;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;

    @Transactional
    public InitUploadResponse initUpload(String appId, InitUploadRequest request, String userId) {
        log.info("Initializing multipart upload for user: {}, file: {}, size: {}",
                userId, request.getFileName(), request.getFileSize());

        String targetBucketName = multipartUploadStorageService.resolveUploadBucketName();
        tenantDomainService.checkQuota(appId, request.getFileSize());
        fileTypeValidator.validateFile(request.getFileName(), request.getContentType(), request.getFileSize());

        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            Optional<UploadTask> existingTaskOpt = uploadTaskQueryService.findByUserIdAndFileHash(
                    appId,
                    userId,
                    request.getFileHash()
            );
            if (existingTaskOpt.isPresent()) {
                UploadTask existingTask = existingTaskOpt.get();
                if (existingTask.getStatus() == UploadTaskStatus.UPLOADING) {
                    return InitUploadResponse.builder()
                            .taskId(existingTask.getId())
                            .uploadId(existingTask.getUploadId())
                            .chunkSize(existingTask.getChunkSize())
                            .totalParts(existingTask.getTotalParts())
                            .completedParts(uploadPartStateQueryService.findCompletedPartNumbers(existingTask.getId()))
                            .build();
                }
            }
        }

        String storagePath = multipartUploadObjectFactory.generateStoragePath(appId, userId, request.getFileName());
        String uploadId = multipartUploadStorageService.createMultipartUpload(
                storagePath,
                request.getContentType(),
                targetBucketName
        );

        int chunkSize = multipartProperties.getChunkSize();
        int totalParts = (int) Math.ceil((double) request.getFileSize() / chunkSize);
        if (totalParts > multipartProperties.getMaxParts()) {
            throw new BusinessException(
                    FileServiceErrorCodes.PART_COUNT_EXCEEDED,
                    String.format(FileServiceErrorMessages.PART_COUNT_EXCEEDED, multipartProperties.getMaxParts())
            );
        }

        UploadTask task = uploadTaskCommandService.createUploadingTask(
                appId,
                userId,
                request.getFileName(),
                request.getFileSize(),
                request.getFileHash(),
                request.getContentType(),
                storagePath,
                uploadId,
                totalParts,
                chunkSize,
                multipartProperties.getTaskExpireHours()
        );

        return InitUploadResponse.builder()
                .taskId(task.getId())
                .uploadId(uploadId)
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .completedParts(new ArrayList<>())
                .build();
    }
}
