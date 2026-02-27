package com.architectcgz.file.domain.model;

/**
 * Upload task status enum
 */
public enum UploadTaskStatus {
    
    /**
     * Uploading
     */
    UPLOADING("uploading", "Uploading"),
    
    /**
     * Completed
     */
    COMPLETED("completed", "Completed"),
    
    /**
     * Aborted
     */
    ABORTED("aborted", "Aborted"),
    
    /**
     * Expired
     */
    EXPIRED("expired", "Expired");
    
    private final String code;
    private final String description;
    
    UploadTaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get status from code
     */
    public static UploadTaskStatus fromCode(String code) {
        for (UploadTaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown upload task status code: " + code);
    }
    
    /**
     * Check if terminal status
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == ABORTED || this == EXPIRED;
    }
}
