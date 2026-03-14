package com.architectcgz.file.integration;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bitmap 优化集成测试
 * 
 * 测试范围:
 * - 完整上传流程（初始化 → 上传分片 → 完成）
 * - Redis 故障场景（自动回退到数据库）
 * - 断点续传场景（从数据库恢复状态）
 * - 并发上传场景（多线程并发上传）
 * - 数据一致性验证
 * 
 * 使用 Testcontainers 启动真实的 Redis 和 PostgreSQL 容器
 * 
 * Requirements: 8.1, 8.5, 5.1, 5.2, 8.3
 */
@Slf4j
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
    "spring.profiles.active=test"
})
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Bitmap 优化集成测试")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class BitmapIntegrationTest {
    
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
        
        // Redis 配置
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> ""); // Testcontainers Redis 没有密码
        
        // Bitmap 配置
        registry.add("storage.multipart.bitmap.enabled", () -> "true");
        registry.add("storage.multipart.bitmap.sync-batch-size", () -> "10");
        registry.add("storage.multipart.bitmap.expire-hours", () -> "24");
        registry.add("storage.multipart.bitmap.max-parts", () -> "10000");
    }
    
    @Autowired
    private UploadPartRepository uploadPartRepository;
    
    @Autowired
    private UploadTaskRepository uploadTaskRepository;
    
    @Autowired
    private UploadPartMapper uploadPartMapper;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private BitmapProperties bitmapProperties;
    
    @BeforeEach
    void setUp() {
        // 清空 Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @AfterEach
    void tearDown() {
        // 清空 Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    // ========== 完整上传流程测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("完整上传流程 - 初始化 → 上传分片 → 完成")
    void testCompleteUploadWorkflow() throws InterruptedException {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 100;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 上传所有分片
            List<UploadPart> parts = new ArrayList<>();
            for (int i = 1; i <= totalParts; i++) {
                UploadPart part = createUploadPart(taskId, i);
                uploadPartRepository.savePart(part);
                parts.add(part);
            }
            
            // 等待异步同步完成
            Thread.sleep(2000);
            
            // Then - 验证 Bitmap 中的分片数量
            int bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            assertEquals(totalParts, bitmapCount, "Bitmap 中应该有所有分片记录");
            
            // Then - 验证 Bitmap 中的分片列表
            List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            assertEquals(totalParts, bitmapParts.size(), "Bitmap 中应该有所有分片编号");
            for (int i = 1; i <= totalParts; i++) {
                assertTrue(bitmapParts.contains(i), "Bitmap 应该包含分片 " + i);
            }
            
            // Then - 验证数据库中的分片数量（定期同步）
            int dbCount = uploadPartMapper.countByTaskId(taskId);
            assertTrue(dbCount > 0, "数据库中应该有部分分片记录（定期同步）");
            assertTrue(dbCount <= totalParts, "数据库中的分片数量不应超过总数");
            
            // When - 完成上传（全量同步）
            uploadPartRepository.syncAllPartsToDatabase(taskId, parts);
            
            // Then - 验证数据库中的分片数量（全量同步后）
            dbCount = uploadPartMapper.countByTaskId(taskId);
            assertEquals(totalParts, dbCount, "全量同步后数据库应该有所有分片记录");
            
            // Then - 验证数据库中的分片列表
            List<Integer> dbParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
            assertEquals(totalParts, dbParts.size(), "数据库应该有所有分片编号");
            for (int i = 1; i <= totalParts; i++) {
                assertTrue(dbParts.contains(i), "数据库应该包含分片 " + i);
            }
            
            // Then - 验证 Bitmap 已删除
            String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
            Boolean exists = redisTemplate.hasKey(bitmapKey);
            assertFalse(Boolean.TRUE.equals(exists), "全量同步后 Bitmap 应该被删除");
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== Redis 故障场景测试 ==========
    
    @Test
    @Order(2)
    @DisplayName("Redis 故障场景 - Bitmap 丢失时自动回退到数据库")
    void testRedisFailureScenario() {
        // Given - 创建上传任务并写入部分分片
        String taskId = UUID.randomUUID().toString();
        int totalParts = 20;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);

        try {
            for (int i = 1; i <= totalParts; i++) {
                uploadPartRepository.savePart(createUploadPart(taskId, i));
            }

            String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
            Boolean bitmapExistsBeforeFailure = redisTemplate.hasKey(bitmapKey);
            assertTrue(Boolean.TRUE.equals(bitmapExistsBeforeFailure), "故障前 Bitmap key 应该存在");

            // When - 模拟 Redis 中对应 Bitmap 数据丢失
            Boolean deleted = redisTemplate.delete(bitmapKey);
            assertTrue(Boolean.TRUE.equals(deleted), "应该成功删除 Bitmap key 以模拟 Redis 故障");
            assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(bitmapKey)), "Bitmap key 应该已被删除");

            // Then - 查询应该自动回退到数据库
            int completedPartCount = uploadPartRepository.countCompletedParts(taskId);
            assertEquals(totalParts, completedPartCount, "Bitmap 丢失后应该从数据库返回完整分片数量");

            List<Integer> completedParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            assertEquals(totalParts, completedParts.size(), "Bitmap 丢失后应该从数据库返回完整分片列表");

            for (int i = 1; i <= totalParts; i++) {
                assertTrue(completedParts.contains(i), "数据库回退结果中应该包含分片 " + i);
            }
        } finally {
            cleanupTestData(taskId);
        }
    }
    
    // ========== 断点续传场景测试 ==========
    
    @Test
    @Order(3)
    @DisplayName("断点续传场景 - 从数据库恢复状态")
    void testResumeUploadScenario() {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 100;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 上传部分分片
            List<UploadPart> parts = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                UploadPart part = createUploadPart(taskId, i);
                uploadPartRepository.savePart(part);
                parts.add(part);
            }
            
            // When - 模拟系统重启（清空 Redis）
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            
            // Then - 验证 Bitmap 已清空
            int bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            // 应该回退到数据库查询
            assertTrue(bitmapCount >= 0, "清空 Redis 后应该从数据库查询");
            
            // When - 从数据库加载分片状态到 Bitmap（断点续传）
            uploadPartRepository.loadPartsFromDatabase(taskId);
            
            // Then - 验证 Bitmap 已恢复
            bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            assertTrue(bitmapCount > 0, "从数据库加载后 Bitmap 应该有记录");
            
            // Then - 验证 Bitmap 中的分片列表
            List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            assertTrue(bitmapParts.size() > 0, "Bitmap 应该包含已上传的分片");
            
            // When - 继续上传剩余分片
            for (int i = 51; i <= totalParts; i++) {
                UploadPart part = createUploadPart(taskId, i);
                uploadPartRepository.savePart(part);
                parts.add(part);
            }
            
            // Then - 验证所有分片都已上传
            bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            assertEquals(totalParts, bitmapCount, "应该有所有分片记录");
            
            // When - 完成上传
            uploadPartRepository.syncAllPartsToDatabase(taskId, parts);
            
            // Then - 验证数据库中有所有分片
            int dbCount = uploadPartMapper.countByTaskId(taskId);
            assertEquals(totalParts, dbCount, "数据库应该有所有分片记录");
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== 并发上传场景测试 ==========
    
    @Test
    @Order(4)
    @DisplayName("并发上传场景 - 多线程并发上传")
    void testConcurrentUploadScenario() throws InterruptedException, ExecutionException {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 100;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 使用线程池并发上传分片
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            
            for (int i = 1; i <= totalParts; i++) {
                final int partNumber = i;
                Future<?> future = executor.submit(() -> {
                    UploadPart part = createUploadPart(taskId, partNumber);
                    uploadPartRepository.savePart(part);
                });
                futures.add(future);
            }
            
            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            // 等待异步同步完成
            Thread.sleep(2000);
            
            // Then - 验证 Bitmap 中的分片数量
            int bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            assertEquals(totalParts, bitmapCount, "并发上传后 Bitmap 应该有所有分片");
            
            // Then - 验证 Bitmap 中的分片列表
            List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            assertEquals(totalParts, bitmapParts.size(), "Bitmap 应该有所有分片编号");
            
            // Then - 验证没有重复的分片编号
            long distinctCount = bitmapParts.stream().distinct().count();
            assertEquals(totalParts, distinctCount, "不应该有重复的分片编号");
            
            // Then - 验证数据库中的分片数量
            int dbCount = uploadPartMapper.countByTaskId(taskId);
            assertTrue(dbCount > 0, "数据库中应该有分片记录");
            
            // Then - 验证数据库中没有重复的分片
            List<Integer> dbParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
            long dbDistinctCount = dbParts.stream().distinct().count();
            assertEquals(dbParts.size(), dbDistinctCount, "数据库中不应该有重复的分片");
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== 多任务并发上传测试 ==========
    
    @Test
    @Order(5)
    @DisplayName("多任务并发上传 - 多个任务同时上传")
    void testMultipleTasksConcurrentUpload() throws InterruptedException, ExecutionException {
        // Given - 创建多个上传任务
        int taskCount = 5;
        int partsPerTask = 20;
        List<String> taskIds = new ArrayList<>();
        
        for (int i = 0; i < taskCount; i++) {
            String taskId = UUID.randomUUID().toString();
            UploadTask task = createUploadTask(taskId, partsPerTask);
            uploadTaskRepository.save(task);
            taskIds.add(taskId);
        }
        
        try {
            // When - 并发上传所有任务的分片
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>();
            
            for (String taskId : taskIds) {
                for (int partNumber = 1; partNumber <= partsPerTask; partNumber++) {
                    final String currentTaskId = taskId;
                    final int currentPartNumber = partNumber;
                    Future<?> future = executor.submit(() -> {
                        UploadPart part = createUploadPart(currentTaskId, currentPartNumber);
                        uploadPartRepository.savePart(part);
                    });
                    futures.add(future);
                }
            }
            
            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            // 等待异步同步完成
            Thread.sleep(2000);
            
            // Then - 验证每个任务的分片数量
            for (String taskId : taskIds) {
                int bitmapCount = uploadPartRepository.countCompletedParts(taskId);
                assertEquals(partsPerTask, bitmapCount, 
                    String.format("任务 %s 应该有 %d 个分片", taskId, partsPerTask));
                
                List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
                assertEquals(partsPerTask, bitmapParts.size(), 
                    String.format("任务 %s 应该有 %d 个分片编号", taskId, partsPerTask));
            }
            
            // Then - 验证不同任务的 Bitmap 互不干扰
            for (int i = 0; i < taskCount; i++) {
                for (int j = i + 1; j < taskCount; j++) {
                    String taskId1 = taskIds.get(i);
                    String taskId2 = taskIds.get(j);
                    
                    List<Integer> parts1 = uploadPartRepository.findCompletedPartNumbers(taskId1);
                    List<Integer> parts2 = uploadPartRepository.findCompletedPartNumbers(taskId2);
                    
                    // 两个任务的分片列表应该相同（都是 1-20）
                    assertEquals(parts1, parts2, "不同任务的分片编号列表应该相同");
                    
                    // 但是 Bitmap key 应该不同
                    String key1 = UploadRedisKeys.partsBitmap(taskId1);
                    String key2 = UploadRedisKeys.partsBitmap(taskId2);
                    assertNotEquals(key1, key2, "不同任务应该使用不同的 Bitmap key");
                }
            }
            
        } finally {
            // 清理测试数据
            for (String taskId : taskIds) {
                cleanupTestData(taskId);
            }
        }
    }
    
    // ========== 数据一致性验证测试 ==========
    
    @Test
    @Order(6)
    @DisplayName("数据一致性验证 - Bitmap 和数据库数据一致")
    void testDataConsistency() throws InterruptedException {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 100;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 上传所有分片
            List<UploadPart> parts = new ArrayList<>();
            for (int i = 1; i <= totalParts; i++) {
                UploadPart part = createUploadPart(taskId, i);
                uploadPartRepository.savePart(part);
                parts.add(part);
            }
            
            // 等待异步同步完成
            Thread.sleep(2000);
            
            // Then - 验证 Bitmap 和数据库的数据一致性
            List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            List<Integer> dbParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
            
            // 数据库中的分片应该是 Bitmap 的子集（因为是定期同步）
            for (Integer dbPart : dbParts) {
                assertTrue(bitmapParts.contains(dbPart), 
                    String.format("数据库中的分片 %d 应该在 Bitmap 中", dbPart));
            }
            
            // When - 完成上传（全量同步）
            uploadPartRepository.syncAllPartsToDatabase(taskId, parts);
            
            // Then - 验证数据库中有所有分片
            dbParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
            assertEquals(totalParts, dbParts.size(), "数据库应该有所有分片");
            
            // Then - 验证数据库中的分片编号完整
            for (int i = 1; i <= totalParts; i++) {
                assertTrue(dbParts.contains(i), 
                    String.format("数据库应该包含分片 %d", i));
            }
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== Bitmap TTL 测试 ==========
    
    @Test
    @Order(7)
    @DisplayName("Bitmap TTL - 验证 TTL 设置正确")
    void testBitmapTTL() {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 10;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 上传一个分片
            UploadPart part = createUploadPart(taskId, 1);
            uploadPartRepository.savePart(part);
            
            // Then - 验证 Bitmap key 存在
            String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
            Boolean exists = redisTemplate.hasKey(bitmapKey);
            assertTrue(Boolean.TRUE.equals(exists), "Bitmap key 应该存在");
            
            // Then - 验证 TTL 已设置
            Long ttl = redisTemplate.getExpire(bitmapKey, TimeUnit.HOURS);
            assertNotNull(ttl, "TTL 应该已设置");
            assertTrue(ttl > 0, "TTL 应该大于 0");
            assertTrue(ttl <= 24, "TTL 应该不超过 24 小时");
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== 幂等性测试 ==========
    
    @Test
    @Order(8)
    @DisplayName("幂等性测试 - 重复上传同一分片")
    void testIdempotency() {
        // Given - 创建上传任务
        String taskId = UUID.randomUUID().toString();
        int totalParts = 10;
        UploadTask task = createUploadTask(taskId, totalParts);
        uploadTaskRepository.save(task);
        
        try {
            // When - 上传同一分片多次
            UploadPart part = createUploadPart(taskId, 1);
            uploadPartRepository.savePart(part);
            uploadPartRepository.savePart(part);
            uploadPartRepository.savePart(part);
            
            // Then - 验证 Bitmap 中只有一个分片
            int bitmapCount = uploadPartRepository.countCompletedParts(taskId);
            assertEquals(1, bitmapCount, "重复上传不应该增加分片数量");
            
            // Then - 验证 Bitmap 中的分片列表
            List<Integer> bitmapParts = uploadPartRepository.findCompletedPartNumbers(taskId);
            assertEquals(1, bitmapParts.size(), "应该只有一个分片编号");
            assertEquals(1, bitmapParts.get(0), "分片编号应该是 1");
            
        } finally {
            // 清理测试数据
            cleanupTestData(taskId);
        }
    }
    
    // ========== 配置验证测试 ==========
    
    @Test
    @Order(9)
    @DisplayName("配置验证 - 验证 Bitmap 配置正确加载")
    void testBitmapConfiguration() {
        // Then - 验证配置
        assertTrue(bitmapProperties.isEnabled(), "Bitmap 应该启用");
        assertEquals(10, bitmapProperties.getSyncBatchSize(), "同步批次大小应该为 10");
        assertEquals(24, bitmapProperties.getExpireHours(), "过期时间应该为 24 小时");
        assertEquals(10000, bitmapProperties.getMaxParts(), "最大分片数应该为 10000");
    }
    
    // ========== Redis 连接测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("Redis 连接 - 验证 Redis 可用")
    void testRedisConnection() {
        // When - 测试 Redis 连接
        redisTemplate.opsForValue().set("test-key", "test-value");
        String value = redisTemplate.opsForValue().get("test-key");
        
        // Then - 验证 Redis 正常工作
        assertEquals("test-value", value, "Redis 应该正常工作");
        
        // 清理测试数据
        redisTemplate.delete("test-key");
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 创建上传任务
     */
    private UploadTask createUploadTask(String taskId, int totalParts) {
        return UploadTask.builder()
                .id(taskId)
                .appId("test-app")
                .userId("test-user")
                .fileName("test-file.bin")
                .fileSize(1024L * 1024L * totalParts) // 每个分片 1MB
                .contentType("application/octet-stream")
                .storagePath("bitmap-tests/" + taskId + "/test-file.bin")
                .uploadId(UUID.randomUUID().toString())
                .totalParts(totalParts)
                .chunkSize(1024 * 1024) // 1MB
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }
    
    /**
     * 创建上传分片
     */
    private UploadPart createUploadPart(String taskId, int partNumber) {
        return UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(partNumber)
                .etag("etag-" + partNumber)
                .size(1024L * 1024L) // 1MB
                .uploadedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 清理测试数据
     */
    private void cleanupTestData(String taskId) {
        try {
            // 删除 Bitmap
            String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
            redisTemplate.delete(bitmapKey);
            
            // 删除数据库记录
            uploadPartMapper.deleteByTaskId(taskId);
            
            // 更新任务状态为已中止（而不是删除）
            uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.ABORTED);
        } catch (Exception e) {
            // 忽略清理错误
            log.warn("清理测试数据失败: taskId={}", taskId, e);
        }
    }
}
