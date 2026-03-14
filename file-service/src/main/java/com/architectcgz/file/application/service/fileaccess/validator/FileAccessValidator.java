package com.architectcgz.file.application.service.fileaccess.validator;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件访问校验器。
 */
@Slf4j
@Component
public class FileAccessValidator {

    public void validateFileAccess(FileRecord file, String fileId, String requestUserId, String appId) {
        if (!file.belongsToApp(appId)) {
            log.warn("跨租户访问被拒绝: fileId={}, fileAppId={}, requestAppId={}",
                    file.getId(), file.getAppId(), appId);
            throw FileNotFoundException.notFound(fileId);
        }

        if (file.isDeleted()) {
            log.debug("已删除文件被拒绝访问: fileId={}", file.getId());
            throw FileNotFoundException.deleted(fileId);
        }

        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            return;
        }

        if (file.getAccessLevel() == AccessLevel.PRIVATE) {
            if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
                throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_FILE, fileId));
            }
            return;
        }

        throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_FILE, fileId));
    }

    public void validateOwnerCanUpdateAccessLevel(FileRecord file, String fileId, String requestUserId) {
        if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
            throw new AccessDeniedException(String.format(
                    FileServiceErrorMessages.ACCESS_DENIED_UPDATE_ACCESS_LEVEL, fileId
            ));
        }
    }
}
