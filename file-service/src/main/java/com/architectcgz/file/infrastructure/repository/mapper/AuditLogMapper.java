package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.AuditLogPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis mapper for audit log operations
 */
@Mapper
public interface AuditLogMapper {
    /**
     * Insert an audit log entry
     * 
     * @param auditLog the audit log to insert
     * @return number of rows affected
     */
    int insert(AuditLogPO auditLog);
}
