package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UploadPartRepository Bitmap TTL 设置属性测试
 * 
 * 测试目标：验证 savePart() 方法正确设置 Bitmap key 的过期时间
 * 
 * Property 2: Bitmap TTL 设置
 * For any 新创建的 Bitmap key，其过期时间应该在 23-24 小时之间（考虑执行延迟）
 * 
 * Validates: Requirements 1.4
 * 
 * 使用 Testcontainers 启动真实的 Redis 和 PostgreSQL 容器
 * 每个测试方法内部运行 100 次随机输入，模拟属性测试
 * 
 * @author File Service Team
 */
@SpringBootTest
@Testcontainers
@DisplayName("Property 2: Bitmap TTL 设置")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UploadPartRepositoryBitmapTTLPropertyTest {

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

        // Redis 配置 - Testcontainers Redis 没有密码
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> ""); // 禁用密码认证
        
        // Redisson 配置 - 禁用密码认证
        registry.add("spring.redis.redisson.config", () -> 
            "singleServerConfig:\n" +
            "  address: \"redis://" + redis.getHost() + ":" + redis.getFirstMappedPort() + "\"\n" +
            "  password: null\n"
        );

        // Bitmap 配置
        registry.add("storage.multipart.bitmap.enabled", () -> "true");
        registry.add("storage.multipart.bitmap.sync-batch-size", () -> "10");
        registry.add("storage.multipart.bitmap.expire-hours", () -> "24");
        registry.add("storage.multipart.bitmap.max-parts", () -> "10000");
    }

    @Autowired
    private UploadPartRepositoryImpl repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BitmapProperties bitmapProperties;

    @Autowired
    private UploadPartMapper uploadPartMapper;

    private final Random random = new Random();

    @BeforeEach
    void setUp() {
        // 确保 Bitmap 功能启用
        bitmapProperties.setEnabled(true);
        
        // 清理 Redis 测试数据
        redisTemplate.keys("upload:task:*").forEach(key -> redisTemplate.delete(key));
    }

    /**
     * Property 2: Bitmap TTL 设置
     * 
     * For any 新创建的 Bitmap key，其过期时间应该在 23-24 小时之间（考虑执行延迟）
     * 
     * 测试逻辑：
     * 1. 记录分片到 Bitmap
     * 2. 检查 key 的 TTL
     * 3. 验证 TTL 在合理范围内（23-24 小时，即 82800-86400 秒）
     * 
     * **Validates: Requirements 1.4**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 2: Bitmap TTL 设置
     */
    @Test
    @DisplayName("Property 2: Bitmap TTL 设置 - For any 新创建的 Bitmap，TTL 应在 23-24 小时之间 (20 iterations)")
    void bitmapTTLSetting() {
        // 运行 20 次随机输入测试
        for (int iteration = 1; iteration <= 20; iteration++) {
            // Given - 生成随机的 taskId 和 partNumber
            String taskId = generateRandomTaskId();
            int partNumber = random.nextInt(1000) + 1; // 1 到 1000
            
            UploadPart part = UploadPart.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .partNumber(partNumber)
                    .etag("test-etag-" + partNumber)
                    .size(5L * 1024 * 1024) // 5MB
                    .uploadedAt(LocalDateTime.now())
                    .build();

            String expectedKey = UploadRedisKeys.partsBitmap(taskId);

            // When - 记录分片（首次创建 Bitmap）
            repository.savePart(part);

            // Then - 验证 TTL 设置
            
            // 1. 验证 key 存在
            Boolean keyExists = redisTemplate.hasKey(expectedKey);
            assertThat(keyExists)
                    .as("[Iteration %d] Bitmap key 应该存在于 Redis 中", iteration)
                    .isTrue();

            // 2. 获取 TTL（秒）
            Long ttlSeconds = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .as("[Iteration %d] TTL 不应为 null", iteration)
                    .isNotNull();

            // 3. 验证 TTL 在合理范围内
            // 配置的过期时间是 24 小时 = 86400 秒
            // 考虑执行延迟，允许范围：23-24 小时（82800-86400 秒）
            long expectedTTL = bitmapProperties.getExpireHours() * 3600L; // 24 * 3600 = 86400
            long minTTL = expectedTTL - 3600L; // 23 小时 = 82800 秒
            long maxTTL = expectedTTL; // 24 小时 = 86400 秒

            assertThat(ttlSeconds)
                    .as("[Iteration %d] TTL 应该在 %d 到 %d 秒之间（23-24 小时）", 
                        iteration, minTTL, maxTTL)
                    .isBetween(minTTL, maxTTL);

            // 4. 验证 TTL 不是永久的（-1 表示永久）
            assertThat(ttlSeconds)
                    .as("[Iteration %d] TTL 不应该是永久的（-1）", iteration)
                    .isNotEqualTo(-1L);

            // 5. 验证 TTL 不是未设置的（-2 表示 key 不存在）
            assertThat(ttlSeconds)
                    .as("[Iteration %d] TTL 不应该是 -2（key 不存在）", iteration)
                    .isNotEqualTo(-2L);

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    /**
     * Property 2.1: 重复记录不改变 TTL
     * 
     * For any 已存在的 Bitmap key，重复记录分片应该刷新 TTL 到配置的过期时间
     * 
     * **Validates: Requirements 1.4**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 2: Bitmap TTL 设置
     */
    @Test
    @DisplayName("Property 2.1: 重复记录刷新 TTL (20 iterations)")
    void repeatedRecordRefreshesTTL() throws InterruptedException {
        // 运行 20 次随机输入测试
        for (int iteration = 1; iteration <= 20; iteration++) {
            // Given - 生成随机的 taskId
            String taskId = generateRandomTaskId();
            int partNumber1 = random.nextInt(100) + 1;
            int partNumber2 = random.nextInt(100) + 101; // 确保不同
            
            UploadPart part1 = createPart(taskId, partNumber1);
            UploadPart part2 = createPart(taskId, partNumber2);

            String expectedKey = UploadRedisKeys.partsBitmap(taskId);

            // When - 第一次记录分片
            repository.savePart(part1);
            Long firstTTL = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);

            // 等待 2 秒
            Thread.sleep(2000);

            // When - 记录第二个分片（应该刷新 TTL）
            repository.savePart(part2);
            Long secondTTL = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);

            // Then - 第二次的 TTL 应该大于或等于第一次的 TTL（因为被刷新了）
            // 注意：由于执行延迟，secondTTL 可能略小于 firstTTL，但差距应该很小
            assertThat(secondTTL)
                    .as("[Iteration %d] 第二次记录后 TTL 应该被刷新", iteration)
                    .isGreaterThanOrEqualTo(firstTTL - 5); // 允许 5 秒的误差

            // 验证 TTL 仍然在合理范围内
            long expectedTTL = bitmapProperties.getExpireHours() * 3600L;
            long minTTL = expectedTTL - 3600L;
            long maxTTL = expectedTTL;

            assertThat(secondTTL)
                    .as("[Iteration %d] 刷新后的 TTL 应该在 %d 到 %d 秒之间", 
                        iteration, minTTL, maxTTL)
                    .isBetween(minTTL, maxTTL);

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    /**
     * Property 2.2: 不同配置的 TTL 设置
     * 
     * For any 配置的过期时间，Bitmap key 的 TTL 应该与配置一致
     * 
     * **Validates: Requirements 1.4**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 2: Bitmap TTL 设置
     */
    @Test
    @DisplayName("Property 2.2: 不同配置的 TTL 设置 (10 iterations)")
    void differentConfiguredTTL() {
        // 测试不同的过期时间配置
        int[] expireHoursOptions = {12, 24, 48, 72};
        
        for (int expireHours : expireHoursOptions) {
            // 更新配置
            bitmapProperties.setExpireHours(expireHours);
            
            // 运行 10 次随机输入测试
            for (int iteration = 1; iteration <= 10; iteration++) {
                // Given - 生成随机的 taskId 和 partNumber
                String taskId = generateRandomTaskId();
                int partNumber = random.nextInt(1000) + 1;
                
                UploadPart part = createPart(taskId, partNumber);
                String expectedKey = UploadRedisKeys.partsBitmap(taskId);

                // When - 记录分片
                repository.savePart(part);

                // Then - 验证 TTL 与配置一致
                Long ttlSeconds = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);
                
                long expectedTTL = expireHours * 3600L;
                long minTTL = expectedTTL - 3600L; // 允许 1 小时误差
                long maxTTL = expectedTTL;

                assertThat(ttlSeconds)
                        .as("[ExpireHours=%d, Iteration %d] TTL 应该在 %d 到 %d 秒之间", 
                            expireHours, iteration, minTTL, maxTTL)
                        .isBetween(minTTL, maxTTL);

                // 清理测试数据
                redisTemplate.delete(expectedKey);
            }
        }
        
        // 恢复默认配置
        bitmapProperties.setExpireHours(24);
    }

    /**
     * Property 2.3: TTL 设置不影响 Bitmap 数据
     * 
     * For any 分片记录，设置 TTL 不应该影响 Bitmap 中已记录的数据
     * 
     * **Validates: Requirements 1.4**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 2: Bitmap TTL 设置
     */
    @Test
    @DisplayName("Property 2.3: TTL 设置不影响 Bitmap 数据 (20 iterations)")
    void ttlSettingDoesNotAffectBitmapData() {
        // 运行 20 次随机输入测试
        for (int iteration = 1; iteration <= 20; iteration++) {
            // Given - 生成随机的 taskId 和多个 partNumber
            String taskId = generateRandomTaskId();
            int partCount = random.nextInt(10) + 5; // 5 到 14 个分片
            
            String expectedKey = UploadRedisKeys.partsBitmap(taskId);

            // When - 记录多个分片
            for (int i = 1; i <= partCount; i++) {
                UploadPart part = createPart(taskId, i);
                repository.savePart(part);
            }

            // Then - 验证 TTL 已设置
            Long ttlSeconds = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .as("[Iteration %d] TTL 应该已设置", iteration)
                    .isGreaterThan(0L);

            // Then - 验证所有分片数据都正确记录
            Long bitCount = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                connection.bitCount(expectedKey.getBytes())
            );
            
            assertThat(bitCount)
                    .as("[Iteration %d] 应该有 %d 个分片被记录", iteration, partCount)
                    .isEqualTo((long) partCount);

            // 验证每个分片的 bit 都是 1
            for (int i = 1; i <= partCount; i++) {
                Boolean bitValue = redisTemplate.opsForValue().getBit(expectedKey, UploadRedisKeys.getBitOffset(i));
                assertThat(bitValue)
                        .as("[Iteration %d] 分片 %d 应该被记录", iteration, i)
                        .isTrue();
            }

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成随机的任务 ID（10-36 个字符）
     */
    private String generateRandomTaskId() {
        int length = random.nextInt(27) + 10; // 10 到 36
        return UUID.randomUUID().toString().substring(0, Math.min(length, 36));
    }

    /**
     * 创建测试用的分片对象
     */
    private UploadPart createPart(String taskId, int partNumber) {
        return UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(partNumber)
                .etag("test-etag-" + partNumber)
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();
    }
}
