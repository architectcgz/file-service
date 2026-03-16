package com.architectcgz.file.application.service.presigned.bridge;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges legacy presigned-upload APIs onto the new file-core upload session lifecycle.
 */
@Service
@RequiredArgsConstructor
public class PresignedUploadCoreBridgeService {

    private final UploadAppService uploadAppService;
    private final UploadSessionRepository uploadSessionRepository;
    private final BlobObjectRepository blobObjectRepository;
    private final FileAssetRepository fileAssetRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileTypeValidator fileTypeValidator;
    private final PresignedUploadAccessResolver presignedUploadAccessResolver;
    private final PresignedUploadStorageService presignedUploadStorageService;
    private final AccessProperties accessProperties;
    private final MultipartProperties multipartProperties;

    public PresignedUploadResponse getPresignedUploadUrl(String appId, PresignedUploadRequest request, String userId) {
        fileTypeValidator.validateFile(request.getFileName(), request.getContentType(), request.getFileSize());

        var legacyAccessLevel = presignedUploadAccessResolver.resolveAccessLevel(request.getAccessLevel());
        AccessLevel accessLevel = toCoreAccessLevel(legacyAccessLevel);
        String bucketName = presignedUploadStorageService.resolveBucketName(legacyAccessLevel);
        if (blobObjectRepository.findByHash(appId, request.getFileHash(), bucketName).isPresent()) {
            fileRecordRepository.findByUserIdAndFileHash(appId, userId, request.getFileHash())
                    .filter(existingFile -> !existingFile.isDeleted())
                    .ifPresent(existingFile -> {
                        throw new BusinessException(
                                FileServiceErrorCodes.FILE_ALREADY_EXISTS,
                                FileServiceErrorMessages.FILE_ALREADY_EXISTS
                        );
                    });
        }

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                appId,
                userId,
                UploadMode.PRESIGNED_SINGLE,
                accessLevel,
                request.getFileName(),
                request.getContentType(),
                request.getFileSize(),
                request.getFileHash(),
                Duration.ofHours(multipartProperties.getTaskExpireHours()),
                multipartProperties.getChunkSize(),
                multipartProperties.getMaxParts()
        );
        SingleUploadUrlGrant grant = uploadAppService.issueSingleUploadUrl(
                appId,
                creationResult.uploadSession().uploadSessionId(),
                userId,
                Duration.ofSeconds(accessProperties.getPresignedUrlExpireSeconds())
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        return PresignedUploadResponse.builder()
                .presignedUrl(grant.uploadUrl())
                .storagePath(creationResult.uploadSession().objectKey())
                .expiresAt(LocalDateTime.now().plusSeconds(grant.expiresInSeconds()))
                .method("PUT")
                .headers(headers)
                .build();
    }

    public Map<String, String> confirmUpload(String appId, ConfirmUploadRequest request, String userId) {
        var legacyAccessLevel = presignedUploadAccessResolver.resolveAccessLevel(request.getAccessLevel());
        AccessLevel accessLevel = toCoreAccessLevel(legacyAccessLevel);

        UploadSession uploadSession = uploadSessionRepository.findActiveByHash(appId, userId, request.getFileHash())
                .filter(session -> session.uploadMode() == UploadMode.PRESIGNED_SINGLE)
                .filter(session -> session.targetAccessLevel() == accessLevel)
                .filter(session -> request.getStoragePath().equals(session.objectKey()))
                .orElseThrow(() -> new BusinessException(
                        FileServiceErrorCodes.FILE_NOT_UPLOADED,
                        FileServiceErrorMessages.FILE_NOT_UPLOADED
                ));

        UploadCompletion uploadCompletion;
        try {
            uploadCompletion = uploadAppService.completeSingleUpload(
                    appId,
                    uploadSession.uploadSessionId(),
                    userId,
                    null
            );
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.FILE_NOT_UPLOADED, FileServiceErrorMessages.FILE_NOT_UPLOADED, ex);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new BusinessException(FileServiceErrorCodes.ACCESS_DENIED, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK, ex);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, ex.getMessage()),
                    ex
            );
        }

        FileAsset fileAsset = fileAssetRepository.findById(uploadCompletion.fileId())
                .orElseThrow(() -> new BusinessException(
                        FileServiceErrorCodes.FILE_NOT_FOUND,
                        FileServiceErrorMessages.FILE_NOT_UPLOADED
                ));
        String bucketName = presignedUploadStorageService.resolveBucketName(toLegacyAccessLevel(fileAsset.accessLevel()));
        String fileUrl = presignedUploadStorageService.resolveFileUrl(
                toLegacyAccessLevel(fileAsset.accessLevel()),
                bucketName,
                fileAsset.objectKey()
        );

        Map<String, String> result = new HashMap<>();
        result.put("fileId", uploadCompletion.fileId());
        result.put("url", fileUrl);
        return result;
    }

    private BusinessException translateInvalidRequest(UploadSessionInvalidRequestException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("expired")) {
            return new BusinessException(FileServiceErrorCodes.FILE_NOT_UPLOADED, FileServiceErrorMessages.FILE_NOT_UPLOADED, ex);
        }
        if (message != null && message.contains("size mismatch")) {
            return new BusinessException(FileServiceErrorCodes.FILE_SIZE_MISMATCH, FileServiceErrorMessages.FILE_SIZE_MISMATCH, ex);
        }
        return new BusinessException(FileServiceErrorCodes.BUSINESS_ERROR, message, ex);
    }

    private AccessLevel toCoreAccessLevel(com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        return accessLevel == com.architectcgz.file.domain.model.AccessLevel.PRIVATE
                ? AccessLevel.PRIVATE
                : AccessLevel.PUBLIC;
    }

    private com.architectcgz.file.domain.model.AccessLevel toLegacyAccessLevel(AccessLevel accessLevel) {
        return accessLevel == AccessLevel.PRIVATE
                ? com.architectcgz.file.domain.model.AccessLevel.PRIVATE
                : com.architectcgz.file.domain.model.AccessLevel.PUBLIC;
    }
}
