package com.architectcgz.file.common.exception;

/**
 * 访问被拒绝异常
 */
public class AccessDeniedException extends BusinessException {
    
    public AccessDeniedException(String message) {
        super("ACCESS_DENIED", message);
    }
    
    public AccessDeniedException(String fileId, String userId) {
        super("ACCESS_DENIED", 
            String.format("User %s does not have permission to access file %s", userId, fileId));
    }
    
    public AccessDeniedException(String fileId, Long userId) {
        super("ACCESS_DENIED", 
            String.format("User %d does not have permission to access file %s", userId, fileId));
    }
}
