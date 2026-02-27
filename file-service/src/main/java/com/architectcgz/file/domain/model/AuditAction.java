package com.architectcgz.file.domain.model;

/**
 * Enumeration of audit actions that can be performed by administrators
 */
public enum AuditAction {
    DELETE_FILE,
    BATCH_DELETE_FILES,
    CREATE_TENANT,
    UPDATE_TENANT,
    SUSPEND_TENANT,
    UPDATE_QUOTA
}
