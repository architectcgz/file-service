package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;

/**
 * 配额超限异常
 */
public class QuotaExceededException extends BusinessException {
    public QuotaExceededException(String message) {
        super(FileServiceErrorCodes.QUOTA_EXCEEDED, message);
    }

    public QuotaExceededException(String quotaType, long current, long limit) {
        super(FileServiceErrorCodes.QUOTA_EXCEEDED,
            String.format("%s quota exceeded: %d used, limit is %d", quotaType, current, limit));
    }
}
