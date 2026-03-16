package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.AuditLogPO;

/**
 * MyBatis mapper for audit log operations
 */
public interface AuditLogMapper extends RuntimeMyBatisMapper {
    /**
     * Insert an audit log entry
     * 
     * @param auditLog the audit log to insert
     * @return number of rows affected
     */
    int insert(AuditLogPO auditLog);
}
