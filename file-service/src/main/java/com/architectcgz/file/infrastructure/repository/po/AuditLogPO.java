package com.architectcgz.file.infrastructure.repository.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Persistent object for audit log table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogPO {
    private String id;
    private String adminUserId;
    private String action;
    private String targetType;
    private String targetId;
    private String tenantId;
    private String details;  // JSON string
    private String ipAddress;
    private OffsetDateTime createdAt;
}
