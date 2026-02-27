package com.architectcgz.file.common.exception;

/**
 * 文件未找到异常
 * 当请求的文件不存在或已被删除时抛出
 */
public class FileNotFoundException extends BusinessException {
    
    public FileNotFoundException(String message) {
        super("FILE_NOT_FOUND", message);
    }
    
    public FileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 创建文件不存在异常
     */
    public static FileNotFoundException notFound(String fileId) {
        return new FileNotFoundException("文件不存在: " + fileId);
    }
    
    /**
     * 创建文件已删除异常
     */
    public static FileNotFoundException deleted(String fileId) {
        return new FileNotFoundException("文件已被删除: " + fileId);
    }
}
