package com.architectcgz.file.application.service.direct.validator;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 直传任务校验器。
 */
@Component
@RequiredArgsConstructor
public class DirectUploadTaskValidator {

    private final UploadTaskCommandService uploadTaskCommandService;

    public void validateTaskAccess(UploadTask task, String appId, String userId, String deniedMessage) {
        if (!task.getUserId().equals(userId) || !task.getAppId().equals(appId)) {
            throw new AccessDeniedException(deniedMessage);
        }
    }

    public void ensureTaskUploadingAndNotExpired(UploadTask task) {
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(
                    FileServiceErrorCodes.TASK_STATUS_INVALID,
                    String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus())
            );
        }

        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now())) {
            uploadTaskCommandService.markExpired(task.getId());
            throw new BusinessException(
                    FileServiceErrorCodes.UPLOAD_TASK_EXPIRED,
                    FileServiceErrorMessages.UPLOAD_TASK_EXPIRED
            );
        }
    }

    public String requireFileHash(UploadTask task) {
        if (task.getFileHash() == null || task.getFileHash().isBlank()) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPLOAD_TASK_FILE_HASH_MISSING,
                    FileServiceErrorMessages.UPLOAD_TASK_FILE_HASH_MISSING
            );
        }
        return task.getFileHash();
    }

    public void validateUniquePartNumbers(List<Integer> partNumbers) {
        Set<Integer> visited = new HashSet<>();
        for (Integer partNumber : partNumbers) {
            if (!visited.add(partNumber)) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_NUMBER_DUPLICATED,
                        String.format(FileServiceErrorMessages.PART_NUMBER_DUPLICATED, partNumber)
                );
            }
        }
    }

    public void validateCompleteRequestParts(UploadTask task,
                                             List<DirectUploadCompleteRequest.PartInfo> requestParts,
                                             List<S3StorageService.PartInfo> authoritativeParts) {
        if (requestParts == null || requestParts.isEmpty()) {
            return;
        }

        Map<Integer, String> authoritativeEtagByPartNumber = new HashMap<>();
        for (S3StorageService.PartInfo authoritativePart : authoritativeParts) {
            authoritativeEtagByPartNumber.put(authoritativePart.getPartNumber(), authoritativePart.getEtag());
        }

        Set<Integer> visited = new HashSet<>();
        for (DirectUploadCompleteRequest.PartInfo requestPart : requestParts) {
            Integer partNumber = requestPart.getPartNumber();
            if (partNumber == null || partNumber < 1 || partNumber > task.getTotalParts()) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_NUMBER_INVALID,
                        String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, partNumber)
                );
            }
            if (!visited.add(partNumber)) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_NUMBER_DUPLICATED,
                        String.format(FileServiceErrorMessages.PART_NUMBER_DUPLICATED, partNumber)
                );
            }

            String authoritativeEtag = authoritativeEtagByPartNumber.get(partNumber);
            if (authoritativeEtag == null) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_NOT_FOUND_IN_STORAGE,
                        String.format(FileServiceErrorMessages.PART_NOT_FOUND_IN_STORAGE, partNumber)
                );
            }
            if (!authoritativeEtag.equals(requestPart.getEtag())) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_ETAG_MISMATCH,
                        String.format(FileServiceErrorMessages.PART_ETAG_MISMATCH, partNumber)
                );
            }
        }
    }
}
