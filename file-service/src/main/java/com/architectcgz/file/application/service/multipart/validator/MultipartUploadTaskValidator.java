package com.architectcgz.file.application.service.multipart.validator;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import org.springframework.stereotype.Component;

/**
 * 分片上传任务校验器。
 */
@Component
public class MultipartUploadTaskValidator {

    public void validateTaskAccess(UploadTask task, String appId, String userId, String deniedMessage) {
        if (!task.getUserId().equals(userId) || !task.getAppId().equals(appId)) {
            throw new AccessDeniedException(deniedMessage);
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
}
