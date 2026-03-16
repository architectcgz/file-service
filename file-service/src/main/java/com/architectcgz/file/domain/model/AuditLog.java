package com.architectcgz.file.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Domain model for audit log entries
 * Records all administrative operations for compliance and tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    /**
     * Unique identifier for the audit log entry
     */
    private String id;
    
    /**
     * ID of the administrator who performed the action
     */
    private String adminUserId;
    
    /**
     * Type of action performed
     */
    private AuditAction action;
    
    /**
     * Type of target entity
     */
    private TargetType targetType;
    
    /**
     * ID of the target entity
     */
    private String targetId;
    
    /**
     * Tenant ID associated with the operation (if applicable)
     */
    private String tenantId;
    
    /**
     * Additional details about the operation in key-value format
     */
    private Map<String, Object> details;
    
    /**
     * IP address of the administrator
     */
    private String ipAddress;
    
    /**
     * Timestamp when the action was performed
     */
    private OffsetDateTime createdAt;
}
