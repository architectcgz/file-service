package com.architectcgz.file.application.service.presigned.query;

import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.presigned.factory.PresignedUploadObjectFactory;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUploadUrlQueryService {

    private final PresignedUploadAccessResolver presignedUploadAccessResolver;
    private final PresignedStorageObjectQueryService presignedStorageObjectQueryService;
    private final PresignedUploadObjectFactory presignedUploadObjectFactory;
    private final PresignedUploadStorageService presignedUploadStorageService;
    private final FileRecordRepository fileRecordRepository;
    private final FileTypeValidator fileTypeValidator;

    public PresignedUploadResponse getPresignedUploadUrl(String appId, PresignedUploadRequest request, String userId) {
        log.info("Generating presigned upload URL: userId={}, fileName={}, fileSize={}, fileHash={}",
                userId, request.getFileName(), request.getFileSize(), request.getFileHash());

        fileTypeValidator.validateFile(request.getFileName(), request.getContentType(), request.getFileSize());

        var accessLevel = presignedUploadAccessResolver.resolveAccessLevel(request.getAccessLevel());
        String targetBucketName = presignedUploadStorageService.resolveBucketName(accessLevel);

        var existingStorageObject = presignedStorageObjectQueryService.findExistingStorageObject(
                appId, request.getFileHash(), targetBucketName);
        if (existingStorageObject.isPresent()) {
            var existingFileRecord = fileRecordRepository.findByUserIdAndFileHash(appId, userId, request.getFileHash());
            if (existingFileRecord.isPresent() && !existingFileRecord.get().isDeleted()) {
                log.info("File already exists for user (instant upload): userId={}, fileHash={}",
                        userId, request.getFileHash());
                throw new BusinessException(
                        FileServiceErrorCodes.FILE_ALREADY_EXISTS,
                        FileServiceErrorMessages.FILE_ALREADY_EXISTS
                );
            }
        }

        String extension = presignedUploadObjectFactory.getExtension(request.getFileName());
        String storagePath = presignedUploadObjectFactory.generateStoragePath(appId, userId, extension);
        String presignedUrl = presignedUploadStorageService.generatePresignedPutUrl(
                storagePath,
                request.getContentType(),
                targetBucketName
        );

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(
                presignedUploadStorageService.getPresignedUrlExpireSeconds()
        );
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());

        log.info("Presigned upload URL generated: userId={}, storagePath={}, expiresAt={}",
                userId, storagePath, expiresAt);

        return PresignedUploadResponse.builder()
                .presignedUrl(presignedUrl)
                .storagePath(storagePath)
                .expiresAt(expiresAt)
                .method("PUT")
                .headers(headers)
                .build();
    }
}
