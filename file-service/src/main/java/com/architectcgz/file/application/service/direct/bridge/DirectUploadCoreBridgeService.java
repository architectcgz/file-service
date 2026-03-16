package com.architectcgz.file.application.service.direct.bridge;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.uploadsession.UploadSessionInitCoordinatorService;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.CompletedUploadPart;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Maps legacy direct-upload init requests onto the new upload-session core.
 */
@Service
@RequiredArgsConstructor
public class DirectUploadCoreBridgeService {

    private final UploadAppService uploadAppService;
    private final UploadSessionInitCoordinatorService uploadSessionInitCoordinatorService;
    private final ObjectStoragePort objectStoragePort;
    private final AccessProperties accessProperties;
    private final MultipartProperties multipartProperties;

    public DirectUploadInitResponse initDirectUpload(String appId, DirectUploadInitRequest request, String userId) {
        try {
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
            return toLegacyResponse(creationResult);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException | FileAccessMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    public DirectUploadPartUrlResponse getPartUploadUrls(String appId, DirectUploadPartUrlRequest request, String userId) {
        try {
            PartUploadUrlGrant grant = uploadAppService.issuePartUploadUrls(
                    appId,
                    request.getTaskId(),
                    userId,
                    request.getPartNumbers(),
                    Duration.ofSeconds(accessProperties.getPresignedUrlExpireSeconds())
            );
            return DirectUploadPartUrlResponse.builder()
                    .taskId(grant.uploadSessionId())
                    .partUrls(grant.partUrls().stream()
                            .map(partUrl -> DirectUploadPartUrlResponse.PartUrl.builder()
                                    .partNumber(partUrl.partNumber())
                                    .uploadUrl(partUrl.uploadUrl())
                                    .expiresIn(partUrl.expiresInSeconds())
                                    .build())
                            .toList())
                    .build();
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.S3_PRESIGN_PART_FAILED,
                    String.format(FileServiceErrorMessages.S3_PRESIGN_PART_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    public DirectUploadProgressResponse getUploadProgress(String appId, String taskId, String userId) {
        try {
            UploadProgress uploadProgress = uploadAppService.getUploadProgress(appId, taskId, userId);
            return DirectUploadProgressResponse.builder()
                    .taskId(uploadProgress.uploadSessionId())
                    .totalParts(uploadProgress.totalParts())
                    .completedParts(uploadProgress.completedParts())
                    .uploadedBytes(uploadProgress.uploadedBytes())
                    .totalBytes(uploadProgress.totalBytes())
                    .percentage(uploadProgress.percentage())
                    .completedPartNumbers(uploadProgress.uploadedParts().stream()
                            .map(uploadedPart -> uploadedPart.partNumber())
                            .toList())
                    .completedPartInfos(uploadProgress.uploadedParts().stream()
                            .map(uploadedPart -> DirectUploadInitResponse.PartInfo.builder()
                                    .partNumber(uploadedPart.partNumber())
                                    .etag(uploadedPart.etag())
                                    .build())
                            .toList())
                    .build();
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.S3_LIST_PARTS_FAILED,
                    String.format(FileServiceErrorMessages.S3_LIST_PARTS_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    public String completeDirectUpload(String appId, DirectUploadCompleteRequest request, String userId) {
        try {
            UploadCompletion uploadCompletion = uploadAppService.completeSession(
                    appId,
                    request.getTaskId(),
                    userId,
                    request.getContentType(),
                    request.getParts() == null
                            ? List.of()
                            : request.getParts().stream()
                            .map(part -> new CompletedUploadPart(part.getPartNumber(), part.getEtag()))
                            .toList()
            );
            return uploadCompletion.fileId();
        } catch (UploadSessionNotFoundException ex) {
            throw new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND, FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND, ex);
        } catch (UploadSessionAccessDeniedException ex) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        } catch (UploadSessionInvalidRequestException ex) {
            throw translateInvalidRequest(ex);
        } catch (UploadSessionMutationException | FileAccessMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, ex.getMessage()),
                    ex
            );
        }
    }

    private DirectUploadInitResponse toLegacyResponse(UploadSessionCreationResult creationResult) {
        UploadSession uploadSession = creationResult.uploadSession();
        if (creationResult.instantUpload()) {
            return DirectUploadInitResponse.builder()
                    .isResume(false)
                    .isInstantUpload(true)
                    .fileId(uploadSession.fileId())
                    .fileUrl(objectStoragePort.resolveObjectUri(
                            objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel()),
                            uploadSession.objectKey()
                    ).toString())
                    .completedParts(Collections.emptyList())
                    .completedPartInfos(Collections.emptyList())
                    .build();
        }

        return DirectUploadInitResponse.builder()
                .taskId(uploadSession.uploadSessionId())
                .uploadId(uploadSession.providerUploadId())
                .storagePath(uploadSession.objectKey())
                .chunkSize(uploadSession.chunkSizeBytes())
                .totalParts(uploadSession.totalParts())
                .completedParts(creationResult.uploadedParts().stream()
                        .map(uploadedPart -> uploadedPart.partNumber())
                        .toList())
                .completedPartInfos(creationResult.uploadedParts().stream()
                        .map(uploadedPart -> DirectUploadInitResponse.PartInfo.builder()
                                .partNumber(uploadedPart.partNumber())
                                .etag(uploadedPart.etag())
                                .build())
                        .toList())
                .isResume(creationResult.resumed())
                .isInstantUpload(false)
                .build();
    }

    private BusinessException translateInvalidRequest(UploadSessionInvalidRequestException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("expired")) {
            return new BusinessException(FileServiceErrorCodes.UPLOAD_TASK_EXPIRED, FileServiceErrorMessages.UPLOAD_TASK_EXPIRED, ex);
        }
        if (message != null && message.contains("size mismatch")) {
            return new BusinessException(FileServiceErrorCodes.FILE_SIZE_MISMATCH, FileServiceErrorMessages.FILE_SIZE_MISMATCH, ex);
        }
        if (message != null && message.contains("part count exceeds")) {
            return new BusinessException(
                    FileServiceErrorCodes.PART_COUNT_EXCEEDED,
                    String.format(FileServiceErrorMessages.PART_COUNT_EXCEEDED, multipartProperties.getMaxParts()),
                    ex
            );
        }
        if (message != null && message.contains("duplicate partNumber")) {
            return new BusinessException(FileServiceErrorCodes.PART_NUMBER_DUPLICATED, extractPartNumberMessage(
                    FileServiceErrorMessages.PART_NUMBER_DUPLICATED,
                    message,
                    "duplicate partNumber: "
            ), ex);
        }
        if (message != null && message.contains("partNumber out of range")) {
            return new BusinessException(
                    FileServiceErrorCodes.PART_NUMBER_INVALID,
                    String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, extractTrailingInt(message, "partNumber out of range: ")),
                    ex
            );
        }
        if (message != null && message.contains("uploaded parts incomplete")) {
            int[] counts = extractFraction(message, "uploaded parts incomplete: ");
            return new BusinessException(
                    FileServiceErrorCodes.PARTS_INCOMPLETE,
                    String.format(FileServiceErrorMessages.PARTS_INCOMPLETE, counts[0], counts[1]),
                    ex
            );
        }
        if (message != null && message.contains("part not found in storage")) {
            return new BusinessException(
                    FileServiceErrorCodes.PART_NOT_FOUND_IN_STORAGE,
                    String.format(FileServiceErrorMessages.PART_NOT_FOUND_IN_STORAGE, extractTrailingInt(message, "part not found in storage: ")),
                    ex
            );
        }
        if (message != null && message.contains("part etag mismatch")) {
            return new BusinessException(
                    FileServiceErrorCodes.PART_ETAG_MISMATCH,
                    String.format(FileServiceErrorMessages.PART_ETAG_MISMATCH, extractTrailingInt(message, "part etag mismatch: ")),
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
        if (message != null && message.contains("does not support part upload urls")) {
            return new BusinessException(FileServiceErrorCodes.BUSINESS_ERROR, message, ex);
        }
        return new BusinessException(FileServiceErrorCodes.BUSINESS_ERROR, message, ex);
    }

    private String extractPartNumberMessage(String template, String source, String prefix) {
        return String.format(template, extractTrailingInt(source, prefix));
    }

    private int extractTrailingInt(String source, String prefix) {
        String suffix = source.substring(source.indexOf(prefix) + prefix.length()).trim();
        return Integer.parseInt(suffix);
    }

    private int[] extractFraction(String source, String prefix) {
        String suffix = source.substring(source.indexOf(prefix) + prefix.length()).trim();
        String[] values = suffix.split("/");
        return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1])};
    }
}
