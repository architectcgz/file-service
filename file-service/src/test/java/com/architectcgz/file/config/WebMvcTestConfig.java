package com.architectcgz.file.config;

import com.architectcgz.file.common.exception.GlobalExceptionHandler;
import com.architectcgz.file.infrastructure.config.AdminProperties;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.repository.mapper.AuditLogMapper;
import com.architectcgz.file.infrastructure.repository.mapper.FileRecordMapper;
import com.architectcgz.file.infrastructure.repository.mapper.StorageObjectMapper;
import com.architectcgz.file.infrastructure.repository.mapper.TenantMapper;
import com.architectcgz.file.infrastructure.repository.mapper.TenantUsageMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/**
 * WebMvcTest 测试配置
 * 
 * 用于 @WebMvcTest 测试，避免加载 MyBatis 相关配置
 * Mock 所有 MyBatis Mapper 以防止 ApplicationContext 加载失败
 */
@TestConfiguration
@EnableConfigurationProperties({CacheProperties.class, AdminProperties.class})
@Import(GlobalExceptionHandler.class)
public class WebMvcTestConfig {
    
    // Mock 所有 MyBatis Mapper，防止 @WebMvcTest 尝试加载它们
    @MockBean
    private AuditLogMapper auditLogMapper;
    
    @MockBean
    private FileRecordMapper fileRecordMapper;
    
    @MockBean
    private StorageObjectMapper storageObjectMapper;
    
    @MockBean
    private TenantMapper tenantMapper;
    
    @MockBean
    private TenantUsageMapper tenantUsageMapper;
    
}
