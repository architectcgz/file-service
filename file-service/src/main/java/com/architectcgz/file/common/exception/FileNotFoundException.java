package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;

/**
 * 文件未找到异常
 * 当请求的文件不存在或已被删除时抛出
 */
public class FileNotFoundException extends BusinessException {
    
    public FileNotFoundException(String message) {
        super(FileServiceErrorCodes.FILE_NOT_FOUND, message);
    }
    
    public FileNotFoundException(String message, Throwable cause) {
        super(FileServiceErrorCodes.FILE_NOT_FOUND, message, cause);
    }
    
    /**
     * 创建文件不存在异常
     */
    public static FileNotFoundException notFound(String fileId) {
        return new FileNotFoundException(String.format(FileServiceErrorMessages.FILE_NOT_FOUND_WITH_PATH, fileId));
    }
    
    /**
     * 创建文件已删除异常
     */
    public static FileNotFoundException deleted(String fileId) {
        return new FileNotFoundException(String.format(FileServiceErrorMessages.FILE_DELETED_WITH_ID, fileId));
    }
}
