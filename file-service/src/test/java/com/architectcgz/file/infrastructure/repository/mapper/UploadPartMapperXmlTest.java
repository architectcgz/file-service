package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UploadPartMapper XML 映射测试
 * 
 * 验证 MyBatis XML 映射文件是否正确加载
 */
@Slf4j
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
    "spring.profiles.active=test"
})
@Testcontainers
@DisplayName("UploadPartMapper XML 映射测试")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UploadPartMapperXmlTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("file_service_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private UploadPartMapper uploadPartMapper;
    
    @Test
    @DisplayName("验证 Mapper 注入成功")
    void testMapperInjection() {
        assertNotNull(uploadPartMapper, "UploadPartMapper 应该被成功注入");
    }
    
    @Test
    @DisplayName("验证 batchInsert 方法可用")
    void testBatchInsertMethod() {
        // Given - 创建测试数据
        String taskId = UUID.randomUUID().toString();
        List<UploadPartPO> parts = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            UploadPartPO part = new UploadPartPO();
            part.setId(UUID.randomUUID().toString());
            part.setTaskId(taskId);
            part.setPartNumber(i);
            part.setEtag("etag-" + i);
            part.setSize(1024L * 1024L);
            part.setUploadedAt(LocalDateTime.now());
            parts.add(part);
        }
        
        // When - 执行批量插入
        try {
            uploadPartMapper.batchInsert(parts);
            log.info("批量插入成功: taskId={}, count={}", taskId, parts.size());
        } catch (Exception e) {
            log.error("批量插入失败", e);
            fail("批量插入应该成功，但抛出异常: " + e.getMessage());
        }
        
        // Then - 验证插入结果
        int count = uploadPartMapper.countByTaskId(taskId);
        assertEquals(5, count, "应该插入 5 条记录");
        
        // 清理测试数据
        uploadPartMapper.deleteByTaskId(taskId);
    }
}
