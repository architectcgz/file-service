package com.architectcgz.file.application.service.audit.persistence;

import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审计日志持久化服务。
 */
@Service
@RequiredArgsConstructor
public class AuditLogPersistenceService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog save(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }
}
