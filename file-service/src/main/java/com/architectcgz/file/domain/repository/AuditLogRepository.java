package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.AuditLog;

/**
 * Repository interface for audit log operations
 */
public interface AuditLogRepository {
    /**
     * Save an audit log entry
     * 
     * @param auditLog the audit log to save
     * @return the saved audit log with generated ID
     */
    AuditLog save(AuditLog auditLog);
}
