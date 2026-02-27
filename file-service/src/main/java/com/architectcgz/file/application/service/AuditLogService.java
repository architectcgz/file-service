package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing audit logs
 * Records all administrative operations for compliance and tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {
    
    private final AuditLogRepository auditLogRepository;
    
    /**
     * Record an audit log entry
     * 
     * @param auditLog the audit log to record
     */
    public void log(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
            log.debug("Audit log recorded: action={}, targetType={}, targetId={}, adminUser={}", 
                    auditLog.getAction(), 
                    auditLog.getTargetType(), 
                    auditLog.getTargetId(),
                    auditLog.getAdminUserId());
        } catch (Exception e) {
            // Log the error but don't fail the operation
            // Audit logging should not break business operations
            log.error("Failed to record audit log: action={}, targetType={}, targetId={}", 
                    auditLog.getAction(), 
                    auditLog.getTargetType(), 
                    auditLog.getTargetId(), 
                    e);
        }
    }
}
