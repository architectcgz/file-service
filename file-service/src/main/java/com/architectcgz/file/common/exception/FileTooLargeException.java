package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;

/**
 * 文件过大异常
 */
public class FileTooLargeException extends BusinessException {
    public FileTooLargeException(long fileSize, long maxSize) {
        super(FileServiceErrorCodes.FILE_TOO_LARGE,
            String.format("File size %d bytes exceeds maximum allowed size %d bytes", fileSize, maxSize));
    }
}
