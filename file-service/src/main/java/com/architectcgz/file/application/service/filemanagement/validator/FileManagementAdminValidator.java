package com.architectcgz.file.application.service.filemanagement.validator;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 文件管理管理员身份校验器。
 */
@Component
public class FileManagementAdminValidator {

    public String requireAdminUserId(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new AccessDeniedException(FileServiceErrorMessages.ADMIN_IDENTITY_MISSING);
        }
        return adminUserId;
    }
}
