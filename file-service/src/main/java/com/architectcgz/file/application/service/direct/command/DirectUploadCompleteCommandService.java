package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.service.uploadpart.command.UploadPartSyncCommandService;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.direct.factory.DirectUploadObjectFactory;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadCompleteCommandService {

    private final DirectUploadTaskValidator directUploadTaskValidator;
    private final DirectUploadStorageService directUploadStorageService;
    private final DirectUploadObjectFactory directUploadObjectFactory;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadPartSyncCommandService uploadPartSyncCommandService;
    private final StorageObjectRepository storageObjectRepository;
    private final UploadTransactionHelper uploadTransactionHelper;

    public String completeDirectUpload(String appId, DirectUploadCompleteRequest request, String userId) {
        int requestPartCount = request.getParts() == null ? 0 : request.getParts().size();
        log.info("完成直传上传: taskId={}, parts={}", request.getTaskId(), requestPartCount);

        String targetBucketName = directUploadStorageService.resolveUploadBucketName();
        var task = uploadTaskQueryService.getById(request.getTaskId());

        directUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        directUploadTaskValidator.ensureTaskUploadingAndNotExpired(task);

        List<com.architectcgz.file.infrastructure.storage.S3StorageService.PartInfo> authoritativeParts =
                directUploadStorageService.fetchUploadedPartInfos(task, targetBucketName);
        directUploadTaskValidator.validateCompleteRequestParts(task, request.getParts(), authoritativeParts);

        if (authoritativeParts.size() != task.getTotalParts()) {
            throw new BusinessException(
                    FileServiceErrorCodes.PARTS_INCOMPLETE,
                    String.format(FileServiceErrorMessages.PARTS_INCOMPLETE, authoritativeParts.size(), task.getTotalParts())
            );
        }

        List<CompletedPart> completedParts = authoritativeParts.stream()
                .sorted((p1, p2) -> Integer.compare(p1.getPartNumber(), p2.getPartNumber()))
                .map(part -> CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getEtag())
                        .build())
                .collect(Collectors.toList());

        List<UploadPart> uploadParts = authoritativeParts.stream()
                .map(part -> UploadPart.builder()
                        .id(UuidCreator.getTimeOrderedEpoch().toString())
                        .taskId(task.getId())
                        .partNumber(part.getPartNumber())
                        .etag(part.getEtag())
                        .uploadedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        uploadPartSyncCommandService.syncAllParts(task.getId(), uploadParts);
        directUploadStorageService.completeMultipartUpload(
                task.getStoragePath(),
                task.getUploadId(),
                completedParts,
                targetBucketName
        );

        String fileHash = directUploadTaskValidator.requireFileHash(task);
        Optional<com.architectcgz.file.domain.model.StorageObject> existingStorageObject =
                storageObjectRepository.findByFileHashAndBucket(task.getAppId(), fileHash, targetBucketName);

        if (existingStorageObject.isPresent()) {
            var storageObject = existingStorageObject.get();
            var fileRecord = directUploadObjectFactory.buildFileRecord(
                    task.getAppId(),
                    userId,
                    storageObject.getId(),
                    task.getFileName(),
                    storageObject.getStoragePath(),
                    storageObject.getFileSize(),
                    storageObject.getContentType(),
                    fileHash
            );

            try {
                uploadTransactionHelper.saveCompletedInstantUpload(task, storageObject.getId(), fileRecord);
            } catch (Exception dbEx) {
                directUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
                throw dbEx;
            }

            directUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            return fileRecord.getId();
        }

        var storageObject = directUploadObjectFactory.buildStorageObject(
                task.getAppId(),
                task.getStoragePath(),
                task.getFileSize(),
                request.getContentType(),
                fileHash,
                targetBucketName
        );
        var fileRecord = directUploadObjectFactory.buildFileRecord(
                task.getAppId(),
                userId,
                storageObject.getId(),
                task.getFileName(),
                task.getStoragePath(),
                task.getFileSize(),
                request.getContentType(),
                fileHash
        );

        try {
            uploadTransactionHelper.saveCompletedUpload(task, storageObject, fileRecord);
        } catch (Exception dbEx) {
            directUploadStorageService.cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            throw dbEx;
        }

        return fileRecord.getId();
    }
}
