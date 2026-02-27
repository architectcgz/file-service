package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.AuditAction;
import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.model.TargetType;
import com.architectcgz.file.domain.repository.AuditLogRepository;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuditLogService 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证审计日志记录的正确性属性
 */
class AuditLogServicePropertyTest {

    /**
     * Feature: file-service-optimization, Property 28: 审计日志完整记录
     * 
     * 属性：对于任何管理员操作（删除文件、创建租户、更新租户等），系统应该记录审计日志，
     * 包含操作者 ID、操作类型、目标类型、目标 ID、租户 ID、详细信息、IP 地址和时间戳。
     * 
     * 验证需求：10.1
     */
    @Property(tries = 100)
    @Label("Property 28: 审计日志完整记录 - 所有管理员操作应记录完整的审计日志")
    void auditLogCompleteRecording(
            @ForAll("adminOperations") AdminOperation operation
    ) {
        // Given: 创建 AuditLogService 和 mock repository
        AuditLogRepository mockRepository = mock(AuditLogRepository.class);
        
        // 捕获保存的审计日志
        AuditLog[] capturedLog = new AuditLog[1];
        when(mockRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            // 模拟数据库生成 ID 和时间戳
            if (log.getId() == null) {
                log.setId(UUID.randomUUID().toString());
            }
            if (log.getCreatedAt() == null) {
                log.setCreatedAt(LocalDateTime.now());
            }
            capturedLog[0] = log;
            return log;
        });
        
        AuditLogService service = new AuditLogService(mockRepository);
        
        // When: 记录审计日志
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(operation.getAdminUserId())
                .action(operation.getAction())
                .targetType(operation.getTargetType())
                .targetId(operation.getTargetId())
                .tenantId(operation.getTenantId())
                .details(operation.getDetails())
                .ipAddress(operation.getIpAddress())
                .build();
        
        service.log(auditLog);
        
        // Then: 验证审计日志被保存
        verify(mockRepository, times(1)).save(any(AuditLog.class));
        
        // 验证保存的审计日志包含所有必需字段
        assertNotNull(capturedLog[0], "Audit log should be saved");
        
        // 验证操作者 ID
        assertNotNull(capturedLog[0].getAdminUserId(), 
                "Admin user ID should not be null");
        assertEquals(operation.getAdminUserId(), capturedLog[0].getAdminUserId(),
                "Admin user ID should match");
        
        // 验证操作类型
        assertNotNull(capturedLog[0].getAction(), 
                "Action should not be null");
        assertEquals(operation.getAction(), capturedLog[0].getAction(),
                "Action should match");
        
        // 验证目标类型
        assertNotNull(capturedLog[0].getTargetType(), 
                "Target type should not be null");
        assertEquals(operation.getTargetType(), capturedLog[0].getTargetType(),
                "Target type should match");
        
        // 验证目标 ID
        assertNotNull(capturedLog[0].getTargetId(), 
                "Target ID should not be null");
        assertEquals(operation.getTargetId(), capturedLog[0].getTargetId(),
                "Target ID should match");
        
        // 验证租户 ID（如果适用）
        if (operation.getTenantId() != null) {
            assertEquals(operation.getTenantId(), capturedLog[0].getTenantId(),
                    "Tenant ID should match when provided");
        }
        
        // 验证详细信息
        assertNotNull(capturedLog[0].getDetails(), 
                "Details should not be null");
        assertEquals(operation.getDetails().size(), capturedLog[0].getDetails().size(),
                "Details should contain all provided information");
        
        // 验证 IP 地址
        assertNotNull(capturedLog[0].getIpAddress(), 
                "IP address should not be null");
        assertEquals(operation.getIpAddress(), capturedLog[0].getIpAddress(),
                "IP address should match");
        
        // 验证时间戳（由 repository 设置）
        assertNotNull(capturedLog[0].getCreatedAt(), 
                "Created timestamp should be set");
        
        // 验证 ID（由 repository 生成）
        assertNotNull(capturedLog[0].getId(), 
                "Audit log ID should be generated");
    }

    /**
     * 验证审计日志记录失败不会影响业务操作
     */
    @Property(tries = 50)
    @Label("审计日志记录失败不应中断业务操作")
    void auditLogFailureDoesNotBreakOperation(
            @ForAll("adminOperations") AdminOperation operation
    ) {
        // Given: 创建会抛出异常的 repository
        AuditLogRepository mockRepository = mock(AuditLogRepository.class);
        when(mockRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        AuditLogService service = new AuditLogService(mockRepository);
        
        // When: 尝试记录审计日志
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(operation.getAdminUserId())
                .action(operation.getAction())
                .targetType(operation.getTargetType())
                .targetId(operation.getTargetId())
                .tenantId(operation.getTenantId())
                .details(operation.getDetails())
                .ipAddress(operation.getIpAddress())
                .build();
        
        // Then: 不应该抛出异常（审计日志失败不应中断业务）
        assertDoesNotThrow(() -> service.log(auditLog),
                "Audit log failure should not throw exception");
        
        // 验证尝试保存了审计日志
        verify(mockRepository, times(1)).save(any(AuditLog.class));
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成管理员操作
     */
    @Provide
    Arbitrary<AdminOperation> adminOperations() {
        return Combinators.combine(
                adminUserIds(),
                auditActions(),
                targetTypes(),
                targetIds(),
                tenantIds(),
                operationDetails(),
                ipAddresses()
        ).as(AdminOperation::new);
    }

    /**
     * 生成管理员用户 ID
     */
    @Provide
    Arbitrary<String> adminUserIds() {
        return Arbitraries.of(
                "admin-001", "admin-002", "admin-003", 
                "system-admin", "super-admin", "operator-001"
        );
    }

    /**
     * 生成审计操作类型
     */
    @Provide
    Arbitrary<AuditAction> auditActions() {
        return Arbitraries.of(AuditAction.values());
    }

    /**
     * 生成目标类型
     */
    @Provide
    Arbitrary<TargetType> targetTypes() {
        return Arbitraries.of(TargetType.values());
    }

    /**
     * 生成目标 ID
     */
    @Provide
    Arbitrary<String> targetIds() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofLength(8),
                Arbitraries.integers().between(1, 9999)
        ).as((prefix, num) -> prefix + "-" + num);
    }

    /**
     * 生成租户 ID
     */
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.of("blog", "im", "forum", "shop", "cms", "admin", null);
    }

    /**
     * 生成操作详细信息
     */
    @Provide
    Arbitrary<Map<String, Object>> operationDetails() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.longs().between(1L, 1000000000L),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50)
        ).as((key1, value1, value2) -> {
            Map<String, Object> details = new HashMap<>();
            details.put("operation", key1);
            details.put("size", value1);
            details.put("description", value2);
            return details;
        });
    }

    /**
     * 生成 IP 地址
     */
    @Provide
    Arbitrary<String> ipAddresses() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(1, 255)
        ).as((a, b, c, d) -> String.format("%d.%d.%d.%d", a, b, c, d));
    }

    // ========== Helper Classes ==========

    /**
     * 管理员操作数据类
     */
    static class AdminOperation {
        private final String adminUserId;
        private final AuditAction action;
        private final TargetType targetType;
        private final String targetId;
        private final String tenantId;
        private final Map<String, Object> details;
        private final String ipAddress;

        AdminOperation(
                String adminUserId,
                AuditAction action,
                TargetType targetType,
                String targetId,
                String tenantId,
                Map<String, Object> details,
                String ipAddress
        ) {
            this.adminUserId = adminUserId;
            this.action = action;
            this.targetType = targetType;
            this.targetId = targetId;
            this.tenantId = tenantId;
            this.details = details;
            this.ipAddress = ipAddress;
        }

        String getAdminUserId() {
            return adminUserId;
        }

        AuditAction getAction() {
            return action;
        }

        TargetType getTargetType() {
            return targetType;
        }

        String getTargetId() {
            return targetId;
        }

        String getTenantId() {
            return tenantId;
        }

        Map<String, Object> getDetails() {
            return details;
        }

        String getIpAddress() {
            return ipAddress;
        }
    }
}
