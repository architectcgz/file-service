package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.direct.assembler.DirectUploadPartResponseAssembler;
import com.architectcgz.file.application.service.direct.factory.DirectUploadObjectFactory;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadInitCommandService {

    private final DirectUploadStorageService directUploadStorageService;
    private final DirectUploadObjectFactory directUploadObjectFactory;
    private final DirectUploadPartResponseAssembler directUploadPartResponseAssembler;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadTaskCommandService uploadTaskCommandService;
    private final StorageObjectRepository storageObjectRepository;
    private final MultipartProperties multipartProperties;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final UploadTransactionHelper uploadTransactionHelper;

    public DirectUploadInitResponse initDirectUpload(String appId, DirectUploadInitRequest request, String userId) {
        log.info("初始化直传上传: appId={}, userId={}, fileName={}, fileSize={}, fileHash={}",
                appId, userId, request.getFileName(), request.getFileSize(), request.getFileHash());

        String targetBucketName = directUploadStorageService.resolveUploadBucketName();
        tenantDomainService.checkQuota(appId, request.getFileSize());

        fileTypeValidator.validateFile(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );

        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            log.info("检查秒传: appId={}, fileHash={}", appId, request.getFileHash());

            Optional<StorageObject> existingStorageObject = storageObjectRepository
                    .findByFileHashAndBucket(appId, request.getFileHash(), targetBucketName);

            if (existingStorageObject.isPresent()) {
                StorageObject storageObject = existingStorageObject.get();
                if (!request.getFileSize().equals(storageObject.getFileSize())) {
                    throw new BusinessException(
                            FileServiceErrorCodes.FILE_SIZE_MISMATCH,
                            FileServiceErrorMessages.FILE_SIZE_MISMATCH
                    );
                }

                var fileRecord = directUploadObjectFactory.buildFileRecord(
                        appId,
                        userId,
                        storageObject.getId(),
                        request.getFileName(),
                        storageObject.getStoragePath(),
                        storageObject.getFileSize(),
                        storageObject.getContentType(),
                        storageObject.getFileHash()
                );
                uploadTransactionHelper.saveInstantUpload(storageObject.getId(), fileRecord, storageObject.getFileSize());

                return DirectUploadInitResponse.builder()
                        .isInstantUpload(true)
                        .fileId(fileRecord.getId())
                        .fileUrl(directUploadStorageService.getPublicUrl(
                                storageObject.getBucketName(),
                                storageObject.getStoragePath()
                        ))
                        .build();
            }
        }

        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            log.info("查询断点续传任务: appId={}, userId={}, fileHash={}", appId, userId, request.getFileHash());
            Optional<UploadTask> existingTask = uploadTaskQueryService.findByUserIdAndFileHash(
                    appId,
                    userId,
                    request.getFileHash()
            );

            if (existingTask.isPresent()) {
                UploadTask task = existingTask.get();
                if (task.getExpiresAt().isBefore(LocalDateTime.now())) {
                    log.info("上传任务已过期，中止旧任务并创建新任务: taskId={}, expiresAt={}",
                            task.getId(), task.getExpiresAt());
                    try {
                        directUploadStorageService.abortMultipartUpload(
                                task.getStoragePath(),
                                task.getUploadId(),
                                targetBucketName
                        );
                    } catch (Exception e) {
                        log.warn("中止过期任务失败（可能已被清理）: taskId={}, error={}",
                                task.getId(), e.getMessage());
                    }
                    uploadTaskCommandService.markAborted(task.getId());
                } else if (task.canResume()) {
                    if (!request.getFileSize().equals(task.getFileSize())) {
                        log.warn("文件大小不匹配，无法续传: taskId={}, expected={}, actual={}",
                                task.getId(), task.getFileSize(), request.getFileSize());
                        throw new BusinessException(
                                FileServiceErrorCodes.FILE_SIZE_MISMATCH,
                            FileServiceErrorMessages.FILE_SIZE_MISMATCH
                        );
                    }

                    var s3PartInfos = directUploadStorageService.fetchUploadedPartInfos(task, targetBucketName);
                    var completedParts = directUploadPartResponseAssembler.extractCompletedPartNumbers(s3PartInfos);
                    var completedPartInfos = directUploadPartResponseAssembler.toResponsePartInfos(s3PartInfos);

                    return DirectUploadInitResponse.builder()
                            .taskId(task.getId())
                            .uploadId(task.getUploadId())
                            .storagePath(task.getStoragePath())
                            .chunkSize(task.getChunkSize())
                            .totalParts(task.getTotalParts())
                            .completedParts(completedParts)
                            .completedPartInfos(completedPartInfos)
                            .isResume(true)
                            .isInstantUpload(false)
                            .build();
                }
            }
        }

        String storagePath = directUploadObjectFactory.generateStoragePath(appId, userId, request.getFileName());
        String uploadId = directUploadStorageService.createMultipartUpload(
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

        return DirectUploadInitResponse.builder()
                .taskId(task.getId())
                .uploadId(uploadId)
                .storagePath(storagePath)
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .completedParts(Collections.emptyList())
                .completedPartInfos(Collections.emptyList())
                .isResume(false)
                .isInstantUpload(false)
                .build();
    }
}
