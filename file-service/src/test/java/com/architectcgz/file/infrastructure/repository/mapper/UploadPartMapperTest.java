package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UploadPartMapper 单元测试
 * 
 * 测试目标：
 * 1. 测试批量插入功能
 * 2. 测试幂等性（重复插入）
 * 3. 测试查询方法
 * 
 * Requirements: 4.2
 * 
 * 使用 @SpringBootTest 进行 MyBatis Mapper 层测试
 * 使用 Testcontainers 启动真实的 PostgreSQL 和 Redis 容器
 * 
 * @author File Service Team
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("UploadPartMapper 单元测试")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UploadPartMapperTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("file_service_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL 配置
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // MyBatis 配置
        registry.add("mybatis.mapper-locations", () -> "classpath:mapper/*.xml");
        registry.add("mybatis.type-aliases-package", () -> "com.architectcgz.file.infrastructure.repository.po");
        registry.add("mybatis.configuration.map-underscore-to-camel-case", () -> "true");
        
        // Redis 配置 - Testcontainers Redis 没有密码
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
        
        // Redisson 配置 - 禁用密码认证
        registry.add("spring.redis.redisson.config", () -> 
            "singleServerConfig:\n" +
            "  address: \"redis://" + redis.getHost() + ":" + redis.getFirstMappedPort() + "\"\n" +
            "  password: null\n"
        );
        
        // 禁用 Bitmap 功能（仅测试 Mapper）
        registry.add("storage.multipart.bitmap.enabled", () -> "false");
    }

    @Autowired
    private UploadPartMapper uploadPartMapper;

    private String testTaskId;

    @BeforeEach
    void setUp() {
        // 生成测试用的 taskId
        testTaskId = UUID.randomUUID().toString();
        
        // 清理测试数据
        uploadPartMapper.deleteByTaskId(testTaskId);
    }

    // ==================== 批量插入测试 ====================

    @Test
    @DisplayName("批量插入 - 应该成功插入多个分片")
    void batchInsert_shouldInsertMultipleParts() {
        // Given - 准备 5 个分片
        List<UploadPartPO> parts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            parts.add(createPartPO(testTaskId, i));
        }

        // When - 批量插入
        uploadPartMapper.batchInsert(parts);

        // Then - 验证插入成功
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count)
                .as("应该插入 5 个分片")
                .isEqualTo(5);

        // 验证所有分片都能查询到
        List<UploadPartPO> savedParts = uploadPartMapper.selectByTaskId(testTaskId);
        assertThat(savedParts)
                .as("应该能查询到所有插入的分片")
                .hasSize(5);

        // 验证分片编号正确
        List<Integer> partNumbers = savedParts.stream()
                .map(UploadPartPO::getPartNumber)
                .sorted()
                .toList();
        assertThat(partNumbers)
                .as("分片编号应该是 1, 2, 3, 4, 5")
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("批量插入 - 空列表应该不报错")
    void batchInsert_shouldHandleEmptyList() {
        // Given - 空列表
        List<UploadPartPO> parts = new ArrayList<>();

        // When & Then - 不应该抛出异常
        uploadPartMapper.batchInsert(parts);

        // 验证没有插入任何数据
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count)
                .as("空列表不应该插入任何数据")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("批量插入 - 应该保持插入顺序")
    void batchInsert_shouldMaintainInsertionOrder() {
        // Given - 准备乱序的分片（5, 3, 1, 4, 2）
        List<UploadPartPO> parts = List.of(
                createPartPO(testTaskId, 5),
                createPartPO(testTaskId, 3),
                createPartPO(testTaskId, 1),
                createPartPO(testTaskId, 4),
                createPartPO(testTaskId, 2)
        );

        // When - 批量插入
        uploadPartMapper.batchInsert(parts);

        // Then - 查询时应该按 part_number 排序
        List<UploadPartPO> savedParts = uploadPartMapper.selectByTaskId(testTaskId);
        List<Integer> partNumbers = savedParts.stream()
                .map(UploadPartPO::getPartNumber)
                .toList();

        assertThat(partNumbers)
                .as("查询结果应该按分片编号排序")
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("批量插入 - 大批量插入（100个分片）")
    void batchInsert_shouldHandleLargeBatch() {
        // Given - 准备 100 个分片
        List<UploadPartPO> parts = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            parts.add(createPartPO(testTaskId, i));
        }

        // When - 批量插入
        uploadPartMapper.batchInsert(parts);

        // Then - 验证插入成功
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count)
                .as("应该插入 100 个分片")
                .isEqualTo(100);
    }

    // ==================== 幂等性测试 ====================

    @Test
    @DisplayName("幂等性 - insertOrIgnore 重复插入应该被忽略")
    void insertOrIgnore_shouldIgnoreDuplicates() {
        // Given - 准备一个分片
        UploadPartPO part = createPartPO(testTaskId, 1);

        // When - 第一次插入
        uploadPartMapper.insertOrIgnore(part);
        int countAfterFirst = uploadPartMapper.countByTaskId(testTaskId);

        // When - 第二次插入（相同的 taskId 和 partNumber）
        UploadPartPO duplicatePart = createPartPO(testTaskId, 1);
        duplicatePart.setEtag("different-etag"); // 不同的 etag
        uploadPartMapper.insertOrIgnore(duplicatePart);
        int countAfterSecond = uploadPartMapper.countByTaskId(testTaskId);

        // Then - 应该只有一条记录
        assertThat(countAfterFirst)
                .as("第一次插入后应该有 1 条记录")
                .isEqualTo(1);

        assertThat(countAfterSecond)
                .as("第二次插入应该被忽略，仍然只有 1 条记录")
                .isEqualTo(1);

        // 验证保留的是第一次插入的数据
        List<UploadPartPO> savedParts = uploadPartMapper.selectByTaskId(testTaskId);
        assertThat(savedParts.get(0).getEtag())
                .as("应该保留第一次插入的 etag")
                .isEqualTo(part.getEtag());
    }

    @Test
    @DisplayName("幂等性 - batchInsert 重复插入应该被忽略")
    void batchInsert_shouldIgnoreDuplicates() {
        // Given - 第一次批量插入 3 个分片
        List<UploadPartPO> firstBatch = List.of(
                createPartPO(testTaskId, 1),
                createPartPO(testTaskId, 2),
                createPartPO(testTaskId, 3)
        );
        uploadPartMapper.batchInsert(firstBatch);

        // When - 第二次批量插入，包含重复的分片 2 和新的分片 4
        List<UploadPartPO> secondBatch = List.of(
                createPartPO(testTaskId, 2), // 重复
                createPartPO(testTaskId, 4)  // 新的
        );
        uploadPartMapper.batchInsert(secondBatch);

        // Then - 应该有 4 条记录（1, 2, 3, 4）
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count)
                .as("应该有 4 条记录（重复的分片 2 被忽略）")
                .isEqualTo(4);

        // 验证分片编号
        List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(testTaskId);
        assertThat(partNumbers)
                .as("应该包含分片 1, 2, 3, 4")
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("幂等性 - 多次重复插入应该保持幂等")
    void insertOrIgnore_shouldRemainIdempotentAfterMultipleAttempts() {
        // Given - 准备一个分片
        UploadPartPO part = createPartPO(testTaskId, 1);

        // When - 插入 5 次
        for (int i = 0; i < 5; i++) {
            uploadPartMapper.insertOrIgnore(part);
        }

        // Then - 应该只有 1 条记录
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count)
                .as("多次重复插入应该只有 1 条记录")
                .isEqualTo(1);
    }

    // ==================== 查询方法测试 ====================

    @Test
    @DisplayName("查询 - selectByTaskId 应该返回所有分片")
    void selectByTaskId_shouldReturnAllParts() {
        // Given - 插入 3 个分片
        List<UploadPartPO> parts = List.of(
                createPartPO(testTaskId, 1),
                createPartPO(testTaskId, 2),
                createPartPO(testTaskId, 3)
        );
        uploadPartMapper.batchInsert(parts);

        // When - 查询
        List<UploadPartPO> result = uploadPartMapper.selectByTaskId(testTaskId);

        // Then - 验证结果
        assertThat(result)
                .as("应该返回 3 个分片")
                .hasSize(3);

        // 验证字段完整性
        UploadPartPO firstPart = result.get(0);
        assertThat(firstPart.getId()).as("id 不应为空").isNotNull();
        assertThat(firstPart.getTaskId()).as("taskId 应该匹配").isEqualTo(testTaskId);
        assertThat(firstPart.getPartNumber()).as("partNumber 不应为空").isNotNull();
        assertThat(firstPart.getEtag()).as("etag 不应为空").isNotNull();
        assertThat(firstPart.getSize()).as("size 不应为空").isNotNull();
        assertThat(firstPart.getUploadedAt()).as("uploadedAt 不应为空").isNotNull();
    }

    @Test
    @DisplayName("查询 - selectByTaskId 不存在的任务应该返回空列表")
    void selectByTaskId_shouldReturnEmptyListForNonExistentTask() {
        // Given - 不存在的 taskId
        String nonExistentTaskId = UUID.randomUUID().toString();

        // When - 查询
        List<UploadPartPO> result = uploadPartMapper.selectByTaskId(nonExistentTaskId);

        // Then - 应该返回空列表
        assertThat(result)
                .as("不存在的任务应该返回空列表")
                .isEmpty();
    }

    @Test
    @DisplayName("查询 - findPartNumbersByTaskId 应该返回所有分片编号")
    void findPartNumbersByTaskId_shouldReturnAllPartNumbers() {
        // Given - 插入分片 1, 3, 5
        List<UploadPartPO> parts = List.of(
                createPartPO(testTaskId, 1),
                createPartPO(testTaskId, 3),
                createPartPO(testTaskId, 5)
        );
        uploadPartMapper.batchInsert(parts);

        // When - 查询分片编号
        List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(testTaskId);

        // Then - 验证结果
        assertThat(partNumbers)
                .as("应该返回所有分片编号，按升序排列")
                .containsExactly(1, 3, 5);
    }

    @Test
    @DisplayName("查询 - countByTaskId 应该返回正确的数量")
    void countByTaskId_shouldReturnCorrectCount() {
        // Given - 插入 7 个分片
        List<UploadPartPO> parts = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            parts.add(createPartPO(testTaskId, i));
        }
        uploadPartMapper.batchInsert(parts);

        // When - 统计数量
        int count = uploadPartMapper.countByTaskId(testTaskId);

        // Then - 验证结果
        assertThat(count)
                .as("应该返回 7")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("查询 - countByTaskId 不存在的任务应该返回 0")
    void countByTaskId_shouldReturnZeroForNonExistentTask() {
        // Given - 不存在的 taskId
        String nonExistentTaskId = UUID.randomUUID().toString();

        // When - 统计数量
        int count = uploadPartMapper.countByTaskId(nonExistentTaskId);

        // Then - 应该返回 0
        assertThat(count)
                .as("不存在的任务应该返回 0")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("删除 - deleteByTaskId 应该删除所有分片")
    void deleteByTaskId_shouldDeleteAllParts() {
        // Given - 插入 5 个分片
        List<UploadPartPO> parts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            parts.add(createPartPO(testTaskId, i));
        }
        uploadPartMapper.batchInsert(parts);

        // When - 删除
        int deletedCount = uploadPartMapper.deleteByTaskId(testTaskId);

        // Then - 验证删除成功
        assertThat(deletedCount)
                .as("应该删除 5 条记录")
                .isEqualTo(5);

        // 验证数据已被删除
        int remainingCount = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(remainingCount)
                .as("删除后应该没有剩余记录")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("删除 - deleteByTaskId 不存在的任务应该返回 0")
    void deleteByTaskId_shouldReturnZeroForNonExistentTask() {
        // Given - 不存在的 taskId
        String nonExistentTaskId = UUID.randomUUID().toString();

        // When - 删除
        int deletedCount = uploadPartMapper.deleteByTaskId(nonExistentTaskId);

        // Then - 应该返回 0
        assertThat(deletedCount)
                .as("不存在的任务应该返回 0")
                .isEqualTo(0);
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("边界条件 - 插入分片编号为 1")
    void insert_shouldHandlePartNumberOne() {
        // Given - 分片编号为 1
        UploadPartPO part = createPartPO(testTaskId, 1);

        // When - 插入
        uploadPartMapper.insert(part);

        // Then - 验证插入成功
        int count = uploadPartMapper.countByTaskId(testTaskId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("边界条件 - 插入分片编号为 10000")
    void insert_shouldHandleLargePartNumber() {
        // Given - 分片编号为 10000
        UploadPartPO part = createPartPO(testTaskId, 10000);

        // When - 插入
        uploadPartMapper.insert(part);

        // Then - 验证插入成功
        List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(testTaskId);
        assertThat(partNumbers).containsExactly(10000);
    }

    @Test
    @DisplayName("边界条件 - 不同任务的相同分片编号应该独立")
    void insert_shouldHandleSamePartNumberForDifferentTasks() {
        // Given - 两个不同的任务，相同的分片编号
        String taskId1 = UUID.randomUUID().toString();
        String taskId2 = UUID.randomUUID().toString();

        UploadPartPO part1 = createPartPO(taskId1, 1);
        UploadPartPO part2 = createPartPO(taskId2, 1);

        // When - 插入
        uploadPartMapper.insert(part1);
        uploadPartMapper.insert(part2);

        // Then - 两个任务都应该有 1 个分片
        int count1 = uploadPartMapper.countByTaskId(taskId1);
        int count2 = uploadPartMapper.countByTaskId(taskId2);

        assertThat(count1).as("任务 1 应该有 1 个分片").isEqualTo(1);
        assertThat(count2).as("任务 2 应该有 1 个分片").isEqualTo(1);

        // 清理
        uploadPartMapper.deleteByTaskId(taskId1);
        uploadPartMapper.deleteByTaskId(taskId2);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的分片 PO
     */
    private UploadPartPO createPartPO(String taskId, int partNumber) {
        UploadPartPO part = new UploadPartPO();
        part.setId(UUID.randomUUID().toString());
        part.setTaskId(taskId);
        part.setPartNumber(partNumber);
        part.setEtag("etag-" + partNumber + "-" + UUID.randomUUID().toString().substring(0, 8));
        part.setSize(5L * 1024 * 1024); // 5MB
        part.setUploadedAt(LocalDateTime.now());
        return part;
    }
}
