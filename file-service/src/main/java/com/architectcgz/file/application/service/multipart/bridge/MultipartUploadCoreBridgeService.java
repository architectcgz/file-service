package com.architectcgz.file.application.service.multipart.bridge;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.uploadsession.UploadSessionInitCoordinatorService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Bridges legacy server-side multipart APIs onto file-core upload sessions.
 */
@Service
@RequiredArgsConstructor
public class MultipartUploadCoreBridgeService {

    private static final int TASK_LIST_LIMIT = 100;

    private final UploadAppService uploadAppService;
    private final UploadSessionInitCoordinatorService uploadSessionInitCoordinatorService;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final MultipartProperties multipartProperties;

    public InitUploadResponse initUpload(String appId, InitUploadRequest request, String userId) {
        tenantDomainService.validateUploadPrerequisites(appId, request.getFileSize());
        fileTypeValidator.validateFile(request.getFileName(), request.getContentType(), request.getFileSize());

        UploadSessionCreationResult creationResult = uploadSessionInitCoordinatorService.createSession(
                appId,
                userId,
                UploadMode.DIRECT,
                AccessLevel.PUBLIC,
                request.getFileName(),
                request.getContentType(),
                request.getFileSize(),
                request.getFileHash(),
                Duration.ofHours(multipartProperties.getTaskExpireHours()),
                multipartProperties.getChunkSize(),
                multipartProperties.getMaxParts()
        );

        return InitUploadResponse.builder()
                .taskId(creationResult.uploadSession().uploadSessionId())
                .uploadId(creationResult.uploadSession().providerUploadId())
                .chunkSize(creationResult.uploadSession().chunkSizeBytes())
                .totalParts(creationResult.uploadSession().totalParts())
                .completedParts(creationResult.uploadedParts().stream()
                        .map(uploadedPart -> uploadedPart.partNumber())
                        .toList())
                .build();
    }

    public String uploadPart(String appId, String taskId, int partNumber, byte[] data, String userId) {
        try {
            return uploadAppService.uploadPart(appId, taskId, userId, partNumber, data);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.S3_PART_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.S3_PART_UPLOAD_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    public String completeUpload(String appId, String taskId, String userId) {
        try {
            UploadCompletion uploadCompletion = uploadAppService.completeSession(appId, taskId, userId, null, List.of());
            return uploadCompletion.fileId();
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    public void abortUpload(String appId, String taskId, String userId) {
        try {
            uploadAppService.abortSession(appId, taskId, userId);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        }
    }

    public UploadProgressResponse getProgress(String appId, String taskId, String userId) {
        try {
            UploadProgress uploadProgress = uploadAppService.getUploadProgress(appId, taskId, userId);
            return UploadProgressResponse.builder()
                    .taskId(uploadProgress.uploadSessionId())
                    .totalParts(uploadProgress.totalParts())
                    .completedParts(uploadProgress.completedParts())
                    .uploadedBytes(uploadProgress.uploadedBytes())
                    .totalBytes(uploadProgress.totalBytes())
                    .percentage(uploadProgress.percentage())
                    .build();
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK);
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        }
    }

    public List<UploadTask> listTasks(String appId, String userId) {
        return uploadAppService.listVisibleSessions(appId, userId, TASK_LIST_LIMIT).stream()
                .filter(uploadSession -> uploadSession.uploadMode() == UploadMode.DIRECT)
                .map(this::toLegacyTask)
                .toList();
    }

    private UploadTask toLegacyTask(UploadSession uploadSession) {
        return UploadTask.builder()
                .id(uploadSession.uploadSessionId())
                .appId(uploadSession.tenantId())
                .userId(uploadSession.ownerId())
                .fileName(uploadSession.originalFilename())
                .fileSize(uploadSession.expectedSize())
                .fileHash(uploadSession.fileHash())
                .contentType(uploadSession.contentType())
                .storagePath(uploadSession.objectKey())
                .uploadId(uploadSession.providerUploadId())
                .totalParts(uploadSession.totalParts())
                .chunkSize(uploadSession.chunkSizeBytes())
                .status(toLegacyStatus(uploadSession))
                .createdAt(toOffsetDateTime(uploadSession.createdAt()))
                .updatedAt(toOffsetDateTime(uploadSession.updatedAt()))
                .expiresAt(toOffsetDateTime(uploadSession.expiresAt()))
                .build();
    }

    private UploadTaskStatus toLegacyStatus(UploadSession uploadSession) {
        return switch (uploadSession.status()) {
            case COMPLETED -> UploadTaskStatus.COMPLETED;
            case ABORTED -> UploadTaskStatus.ABORTED;
            case EXPIRED, FAILED -> UploadTaskStatus.EXPIRED;
            default -> UploadTaskStatus.UPLOADING;
        };
    }

    private OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private BusinessException translateInvalidRequest(UploadSessionInvalidRequestException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("expired")) {
            return new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_EXPIRED, FileServiceErrorMessages.UPLOAD_TASK_EXPIRED, ex);
        }
        if (message != null && message.contains("partNumber out of range")) {
            String partNumber = message.substring(message.lastIndexOf(':') + 1).trim();
            return new BusinessException(
                    FileServiceErrorCodes.PART_NUMBER_INVALID,
                    String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, Integer.parseInt(partNumber)),
                    ex
            );
        }
        if (message != null && message.contains("uploaded parts incomplete")) {
            String counts = message.substring(message.lastIndexOf(':') + 1).trim();
            String[] values = counts.split("/");
            return new BusinessException(
                    FileServiceErrorCodes.PARTS_INCOMPLETE,
                    String.format(FileServiceErrorMessages.PARTS_INCOMPLETE, Integer.parseInt(values[0]), Integer.parseInt(values[1])),
                    ex
            );
        }
        if (message != null && message.contains("fileHash is required")) {
            return new BusinessException(
                    FileServiceErrorCodes.UPLOAD_TASK_FILE_HASH_MISSING,
                    FileServiceErrorMessages.UPLOAD_TASK_FILE_HASH_MISSING,
                    ex
            );
        }
        if (message != null && message.contains("part not found in storage")) {
            String partNumber = message.substring(message.lastIndexOf(':') + 1).trim();
            return new BusinessException(
                    FileServiceErrorCodes.PART_NOT_FOUND_IN_STORAGE,
                    String.format(FileServiceErrorMessages.PART_NOT_FOUND_IN_STORAGE, Integer.parseInt(partNumber)),
                    ex
            );
        }
        if (message != null && message.contains("part etag mismatch")) {
            String partNumber = message.substring(message.lastIndexOf(':') + 1).trim();
            return new BusinessException(
                    FileServiceErrorCodes.PART_ETAG_MISMATCH,
                    String.format(FileServiceErrorMessages.PART_ETAG_MISMATCH, Integer.parseInt(partNumber)),
                    ex
            );
        }
        if (message != null && message.contains("size mismatch")) {
            return new BusinessException(FileServiceErrorCodes.FILE_SIZE_MISMATCH, FileServiceErrorMessages.FILE_SIZE_MISMATCH, ex);
        }
        return new BusinessException(FileServiceErrorCodes.BUSINESS_ERROR, message, ex);
    }
}
