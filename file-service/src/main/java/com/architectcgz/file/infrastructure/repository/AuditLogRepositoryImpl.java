package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.repository.AuditLogRepository;
import com.architectcgz.file.infrastructure.repository.mapper.AuditLogMapper;
import com.architectcgz.file.infrastructure.repository.po.AuditLogPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of AuditLogRepository using MyBatis
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {
    
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    
    @Override
    public AuditLog save(AuditLog auditLog) {
        // Generate ID if not present
        if (auditLog.getId() == null) {
            auditLog.setId(UUID.randomUUID().toString());
        }
        
        // Set created timestamp if not present
        if (auditLog.getCreatedAt() == null) {
            auditLog.setCreatedAt(LocalDateTime.now());
        }
        
        // Convert to PO
        AuditLogPO po = convertToPO(auditLog);
        
        // Insert into database
        auditLogMapper.insert(po);
        
        return auditLog;
    }
    
    private AuditLogPO convertToPO(AuditLog auditLog) {
        String detailsJson = null;
        if (auditLog.getDetails() != null && !auditLog.getDetails().isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(auditLog.getDetails());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize audit log details", e);
                detailsJson = "{}";
            }
        }
        
        return AuditLogPO.builder()
                .id(auditLog.getId())
                .adminUserId(auditLog.getAdminUserId())
                .action(auditLog.getAction() != null ? auditLog.getAction().name() : null)
                .targetType(auditLog.getTargetType() != null ? auditLog.getTargetType().name() : null)
                .targetId(auditLog.getTargetId())
                .tenantId(auditLog.getTenantId())
                .details(detailsJson)
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
