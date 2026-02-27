package com.architectcgz.file.integration;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.FileAccessService;
import com.architectcgz.file.application.service.FileManagementService;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileRedisKeys;
import com.architectcgz.file.infrastructure.config.CacheProperties;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件 URL 缓存集成测试
 * 
 * 测试范围：
 * - 完整的缓存读写流程
 * - 文件删除后缓存清除
 * - TTL 过期验证
 * - 并发访问场景
 * - Redis 和 PostgreSQL 的真实交互
 * 
 * 使用 Testcontainers 启动真实的 Redis 和 PostgreSQL 容器
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("文件 URL 缓存集成测试")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class FileCacheIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("file_service_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL 配置
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Redis 配置
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        
        // 缓存配置
        registry.add("file-service.cache.enabled", () -> "true");
        registry.add("file-service.cache.url.ttl", () -> "5"); // 5秒，方便测试TTL过期
    }
    
    @Autowired
    private FileAccessService fileAccessService;
    
    @Autowired
    private FileManagementService fileManagementService;
    
    @Autowired
    private FileRecordRepository fileRecordRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private CacheProperties cacheProperties;
    
    private FileRecord testPublicFile;
    private FileRecord testPrivateFile;
    
    @BeforeEach
    void setUp() {
        // 清空 Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // 准备测试数据
        testPublicFile = FileRecord.builder()
                .id("test-file-001")
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-001")
                .originalFilename("test-public.jpg")
                .storagePath("2026/02/11/user-123/test-public.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        testPrivateFile = FileRecord.builder()
                .id("test-file-002")
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-002")
                .originalFilename("test-private.jpg")
                .storagePath("2026/02/11/user-123/test-private.jpg")
                .fileSize(2048L)
                .contentType("image/jpeg")
                .fileHash("def456")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PRIVATE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 保存到数据库
        fileRecordRepository.save(testPublicFile);
        fileRecordRepository.save(testPrivateFile);
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据
        try {
            fileRecordRepository.deleteById(testPublicFile.getId());
        } catch (Exception ignored) {
        }
        try {
            fileRecordRepository.deleteById(testPrivateFile.getId());
        } catch (Exception ignored) {
        }
        
        // 清空 Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    // ========== 完整缓存流程测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("完整缓存流程 - 第一次查询缓存未命中，第二次查询缓存命中")
    void testCompleteCacheFlow() {
        String fileId = testPublicFile.getId();
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        // 1. 验证缓存为空
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNull(cachedUrl, "初始状态缓存应该为空");
        
        // 2. 第一次查询（缓存未命中，查询数据库）
        FileUrlResponse response1 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response1);
        assertNotNull(response1.getUrl());
        assertTrue(response1.getPermanent());
        assertNull(response1.getExpiresAt());
        
        // 3. 验证缓存已写入
        cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedUrl, "第一次查询后缓存应该已写入");
        assertEquals(response1.getUrl(), cachedUrl, "缓存的URL应该与返回的URL一致");
        
        // 4. 第二次查询（缓存命中）
        FileUrlResponse response2 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response2);
        assertEquals(response1.getUrl(), response2.getUrl(), "两次查询应该返回相同的URL");
        
        // 5. 验证缓存仍然存在
        cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedUrl, "第二次查询后缓存应该仍然存在");
    }
    
    // ========== 私有文件不缓存测试 ==========
    
    @Test
    @Order(2)
    @DisplayName("私有文件 - 预签名URL不缓存")
    void testPrivateFileNotCached() {
        String fileId = testPrivateFile.getId();
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        // 1. 查询私有文件URL
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response);
        assertNotNull(response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
        
        // 2. 验证缓存未写入
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNull(cachedUrl, "私有文件的预签名URL不应该被缓存");
    }
    
    // ========== 文件删除后缓存清除测试 ==========
    
    @Test
    @Order(3)
    @DisplayName("文件删除 - 缓存被清除")
    void testCacheClearedAfterFileDeletion() {
        String fileId = testPublicFile.getId();
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        // 1. 第一次查询，写入缓存
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response);
        
        // 2. 验证缓存已写入
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedUrl, "查询后缓存应该已写入");
        
        // 3. 删除文件
        fileManagementService.deleteFile(fileId, "admin-001");
        
        // 4. 验证缓存已清除
        cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNull(cachedUrl, "文件删除后缓存应该被清除");
        
        // 5. 验证文件记录已删除
        assertFalse(fileRecordRepository.findById(fileId).isPresent(), 
                "文件记录应该已从数据库删除");
    }
    
    // ========== TTL 过期测试 ==========
    
    @Test
    @Order(4)
    @DisplayName("TTL 过期 - 缓存自动过期后重新查询数据库")
    void testCacheTTLExpiration() throws InterruptedException {
        String fileId = testPublicFile.getId();
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        // 1. 第一次查询，写入缓存
        FileUrlResponse response1 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response1);
        
        // 2. 验证缓存已写入
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedUrl, "查询后缓存应该已写入");
        
        // 3. 验证 TTL 已设置
        Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 5, "TTL 应该在 0-5 秒之间");
        
        // 4. 等待 TTL 过期（配置的 TTL 是 5 秒）
        Thread.sleep(6000); // 等待 6 秒确保过期
        
        // 5. 验证缓存已过期
        cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNull(cachedUrl, "TTL 过期后缓存应该被清除");
        
        // 6. 再次查询，应该重新从数据库查询并写入缓存
        FileUrlResponse response2 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        assertNotNull(response2);
        assertEquals(response1.getUrl(), response2.getUrl(), "URL 应该保持一致");
        
        // 7. 验证缓存已重新写入
        cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedUrl, "重新查询后缓存应该已写入");
    }
    
    // ========== 缓存一致性测试 ==========
    
    @Test
    @Order(5)
    @DisplayName("缓存一致性 - 缓存的URL与数据库查询的URL一致")
    void testCacheConsistency() {
        String fileId = testPublicFile.getId();
        
        // 1. 第一次查询（缓存未命中）
        FileUrlResponse response1 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // 2. 第二次查询（缓存命中）
        FileUrlResponse response2 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // 3. 清除缓存
        redisTemplate.delete(FileRedisKeys.fileUrl(fileId));
        
        // 4. 第三次查询（缓存未命中，重新查询数据库）
        FileUrlResponse response3 = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // 5. 验证三次查询的URL完全一致
        assertEquals(response1.getUrl(), response2.getUrl(), "第一次和第二次查询的URL应该一致");
        assertEquals(response2.getUrl(), response3.getUrl(), "第二次和第三次查询的URL应该一致");
    }
    
    // ========== 并发访问测试 ==========
    
    @Test
    @Order(6)
    @DisplayName("并发访问 - 多线程同时查询同一文件")
    void testConcurrentAccess() throws InterruptedException {
        String fileId = testPublicFile.getId();
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        FileUrlResponse[] responses = new FileUrlResponse[threadCount];
        
        // 1. 创建多个线程同时查询
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                responses[index] = fileAccessService.getFileUrl("blog", fileId, "user-123");
            });
        }
        
        // 2. 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 3. 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 4. 验证所有响应都不为空
        for (FileUrlResponse response : responses) {
            assertNotNull(response);
            assertNotNull(response.getUrl());
        }
        
        // 5. 验证所有响应的URL一致
        String firstUrl = responses[0].getUrl();
        for (int i = 1; i < threadCount; i++) {
            assertEquals(firstUrl, responses[i].getUrl(), 
                    "并发查询应该返回相同的URL");
        }
        
        // 6. 验证缓存已写入
        String cachedUrl = redisTemplate.opsForValue().get(FileRedisKeys.fileUrl(fileId));
        assertNotNull(cachedUrl, "并发查询后缓存应该已写入");
    }
    
    // ========== 缓存配置测试 ==========
    
    @Test
    @Order(7)
    @DisplayName("缓存配置 - 验证配置正确加载")
    void testCacheConfiguration() {
        // 验证缓存配置
        assertTrue(cacheProperties.isEnabled(), "缓存应该启用");
        assertEquals(5L, cacheProperties.getUrl().getTtl(), "TTL 应该为 5 秒");
    }
    
    // ========== Redis 连接测试 ==========
    
    @Test
    @Order(8)
    @DisplayName("Redis 连接 - 验证 Redis 可用")
    void testRedisConnection() {
        // 测试 Redis 连接
        redisTemplate.opsForValue().set("test-key", "test-value");
        String value = redisTemplate.opsForValue().get("test-key");
        assertEquals("test-value", value, "Redis 应该正常工作");
        
        // 清理测试数据
        redisTemplate.delete("test-key");
    }
    
    // ========== 缓存 Key 格式测试 ==========
    
    @Test
    @Order(9)
    @DisplayName("缓存 Key 格式 - 验证 Key 格式正确")
    void testCacheKeyFormat() {
        String fileId = testPublicFile.getId();
        String expectedKey = "file:" + fileId + ":url";
        
        // 查询文件，写入缓存
        fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // 验证缓存 Key 格式
        String cachedUrl = redisTemplate.opsForValue().get(expectedKey);
        assertNotNull(cachedUrl, "缓存 Key 格式应该正确");
    }
    
    // ========== 多文件缓存测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("多文件缓存 - 不同文件使用不同的缓存 Key")
    void testMultipleFilesCaching() {
        String fileId1 = testPublicFile.getId();
        String fileId2 = "test-file-003";
        
        // 创建第二个测试文件
        FileRecord testFile2 = FileRecord.builder()
                .id(fileId2)
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-003")
                .originalFilename("test-public-2.jpg")
                .storagePath("2026/02/11/user-123/test-public-2.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("ghi789")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        fileRecordRepository.save(testFile2);
        
        try {
            // 查询两个文件
            FileUrlResponse response1 = fileAccessService.getFileUrl("blog", fileId1, "user-123");
            FileUrlResponse response2 = fileAccessService.getFileUrl("blog", fileId2, "user-123");
            
            // 验证两个文件都被缓存
            String cachedUrl1 = redisTemplate.opsForValue().get(FileRedisKeys.fileUrl(fileId1));
            String cachedUrl2 = redisTemplate.opsForValue().get(FileRedisKeys.fileUrl(fileId2));
            
            assertNotNull(cachedUrl1, "文件1应该被缓存");
            assertNotNull(cachedUrl2, "文件2应该被缓存");
            assertNotEquals(cachedUrl1, cachedUrl2, "两个文件的URL应该不同");
            
            // 验证缓存的URL与响应的URL一致
            assertEquals(response1.getUrl(), cachedUrl1);
            assertEquals(response2.getUrl(), cachedUrl2);
        } finally {
            // 清理测试数据
            fileRecordRepository.deleteById(fileId2);
        }
    }
}
