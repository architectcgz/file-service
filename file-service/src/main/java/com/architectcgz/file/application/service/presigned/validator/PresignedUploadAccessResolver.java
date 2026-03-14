package com.architectcgz.file.application.service.presigned.validator;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 预签名上传访问级别解析器。
 */
@Component
public class PresignedUploadAccessResolver {

    public AccessLevel resolveAccessLevel(String rawAccessLevel) {
        if (!StringUtils.hasText(rawAccessLevel)) {
            return AccessLevel.PUBLIC;
        }
        try {
            return AccessLevel.valueOf(rawAccessLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.UNSUPPORTED_ACCESS_LEVEL,
                    "不支持的访问级别: " + rawAccessLevel
            );
        }
    }
}
