package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    
    private final String code;
    
    public BusinessException(String message) {
        super(message);
        this.code = FileServiceErrorCodes.BUSINESS_ERROR;
    }
    
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = FileServiceErrorCodes.BUSINESS_ERROR;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public String getErrorCode() {
        return code;
    }
}
