package com.architectcgz.file.application.service.audit.command;

import com.architectcgz.file.application.service.audit.AuditLogSupport;
import com.architectcgz.file.domain.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志记录服务。
 *
 * 负责持久化审计日志，并保证失败时只记录错误不打断主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogRecordCommandService {

    private final AuditLogSupport auditLogSupport;

    public void log(AuditLog auditLog) {
        try {
            auditLogSupport.save(auditLog);
            log.debug("Audit log recorded: action={}, targetType={}, targetId={}, adminUser={}",
                    auditLog.getAction(),
                    auditLog.getTargetType(),
                    auditLog.getTargetId(),
                    auditLog.getAdminUserId());
        } catch (Exception e) {
            log.error("Failed to record audit log: action={}, targetType={}, targetId={}",
                    auditLog.getAction(),
                    auditLog.getTargetType(),
                    auditLog.getTargetId(),
                    e);
        }
    }
}
