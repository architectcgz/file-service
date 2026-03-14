package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.audit.AuditLogSupport;
import com.architectcgz.file.application.service.audit.command.AuditLogRecordCommandService;
import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 审计日志应用层门面。
 *
 * 对外保留统一审计记录入口，内部委托给 recorder service。
 */
@Service
public class AuditLogService {

    private final AuditLogRecordCommandService auditLogRecordCommandService;

    @Autowired
    public AuditLogService(AuditLogRecordCommandService auditLogRecordCommandService) {
        this.auditLogRecordCommandService = auditLogRecordCommandService;
    }

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this(new AuditLogRecordCommandService(new AuditLogSupport(auditLogRepository)));
    }

    /**
     * 记录审计日志。
     *
     * 审计失败不会影响主业务流程。
     *
     * @param auditLog 审计日志
     */
    public void log(AuditLog auditLog) {
        auditLogRecordCommandService.log(auditLog);
    }
}
