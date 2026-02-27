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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UploadPartRepository Bitmap 记录正确性属性测试
 * 
 * 测试目标：验证 savePart() 方法正确地将分片状态记录到 Redis Bitmap
 * 
 * Property 1: Bitmap 记录正确性
 * For any 上传任务和分片编号，当记录分片状态时，Redis Bitmap 中对应的 bit 位置应该被设置为 1，
 * 且 key 格式为 `upload:task:{taskId}:parts`，bit 偏移量为 `partNumber - 1`
 * 
 * Validates: Requirements 1.1, 1.2, 1.3
 * 
 * 使用 Testcontainers 启动真实的 Redis 和 PostgreSQL 容器
 * 每个测试方法内部运行 100 次随机输入，模拟属性测试
 * 
 * @author File Service Team
 */
@SpringBootTest
@Testcontainers
@DisplayName("Property 1: Bitmap 记录正确性")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UploadPartRepositoryBitmapRecordPropertyTest {

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
        
        // 清理 Redis 测试数据（使用通配符删除所有测试 key）
        // 注意：在生产环境中不要使用 KEYS 命令，这里仅用于测试
        redisTemplate.keys("upload:task:*").forEach(key -> redisTemplate.delete(key));
    }

    /**
     * Property 1: Bitmap 记录正确性
     * 
     * For any 上传任务和分片编号，当记录分片状态时：
     * 1. Redis Bitmap 中对应的 bit 位置应该被设置为 1
     * 2. Key 格式为 `upload:task:{taskId}:parts`
     * 3. Bit 偏移量为 `partNumber - 1`
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 1: Bitmap 记录正确性
     */
    @Test
    @DisplayName("Property 1: Bitmap 记录正确性 - For any 任务和分片，Bitmap 应正确记录状态 (100 iterations)")
    void bitmapRecordCorrectness() {
        // 运行 100 次随机输入测试
        for (int iteration = 1; iteration <= 100; iteration++) {
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

            // When - 记录分片
            repository.savePart(part);

            // Then - 验证 Bitmap 记录
            
            // 1. 验证 Key 格式正确
            String expectedKey = UploadRedisKeys.partsBitmap(taskId);
            assertThat(expectedKey)
                    .as("[Iteration %d] Bitmap key 格式应为 upload:task:{taskId}:parts", iteration)
                    .isEqualTo("upload:task:" + taskId + ":parts");

            // 2. 验证 Bit 偏移量正确
            long expectedOffset = UploadRedisKeys.getBitOffset(partNumber);
            assertThat(expectedOffset)
                    .as("[Iteration %d] Bit 偏移量应为 partNumber - 1", iteration)
                    .isEqualTo(partNumber - 1L);

            // 3. 验证 Bitmap 中对应位置被设置为 1
            Boolean bitValue = redisTemplate.opsForValue().getBit(expectedKey, expectedOffset);
            assertThat(bitValue)
                    .as("[Iteration %d] Bitmap 中 bit[%d] 应该被设置为 true (分片 %d 已上传)", 
                        iteration, expectedOffset, partNumber)
                    .isTrue();

            // 4. 验证 Key 存在且有 TTL
            Boolean keyExists = redisTemplate.hasKey(expectedKey);
            assertThat(keyExists)
                    .as("[Iteration %d] Bitmap key 应该存在于 Redis 中", iteration)
                    .isTrue();

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    /**
     * Property 1.1: Bitmap 记录幂等性
     * 
     * For any 分片，重复记录应该是幂等的，不会产生副作用
     * 
     * **Validates: Requirements 1.1**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 1: Bitmap 记录正确性
     */
    @Test
    @DisplayName("Property 1.1: Bitmap 记录幂等性 - 重复记录不产生副作用 (100 iterations)")
    void bitmapRecordIdempotence() {
        // 运行 100 次随机输入测试
        for (int iteration = 1; iteration <= 100; iteration++) {
            // Given - 生成随机的 taskId 和 partNumber
            String taskId = generateRandomTaskId();
            int partNumber = random.nextInt(1000) + 1;
            
            UploadPart part = UploadPart.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .partNumber(partNumber)
                    .etag("test-etag-" + partNumber)
                    .size(5L * 1024 * 1024)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            String expectedKey = UploadRedisKeys.partsBitmap(taskId);
            long expectedOffset = UploadRedisKeys.getBitOffset(partNumber);

            // When - 第一次记录
            repository.savePart(part);
            Boolean firstValue = redisTemplate.opsForValue().getBit(expectedKey, expectedOffset);

            // When - 第二次记录（重复）
            repository.savePart(part);
            Boolean secondValue = redisTemplate.opsForValue().getBit(expectedKey, expectedOffset);

            // When - 第三次记录（重复）
            repository.savePart(part);
            Boolean thirdValue = redisTemplate.opsForValue().getBit(expectedKey, expectedOffset);

            // Then - 所有值都应该是 true，且保持一致
            assertThat(firstValue)
                    .as("[Iteration %d] 第一次记录后 bit 应为 true", iteration)
                    .isTrue();
            
            assertThat(secondValue)
                    .as("[Iteration %d] 第二次记录后 bit 应保持 true", iteration)
                    .isTrue();
            
            assertThat(thirdValue)
                    .as("[Iteration %d] 第三次记录后 bit 应保持 true", iteration)
                    .isTrue();

            // 验证 BITCOUNT 仍然是 1（没有重复计数）
            Long bitCount = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                connection.bitCount(expectedKey.getBytes())
            );
            
            assertThat(bitCount)
                    .as("[Iteration %d] 重复记录同一分片不应增加 bitCount", iteration)
                    .isEqualTo(1L);

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    /**
     * Property 1.2: 多个分片记录互不干扰
     * 
     * For any 任务的多个不同分片，记录应该互不干扰
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 1: Bitmap 记录正确性
     */
    @Test
    @DisplayName("Property 1.2: 多个分片记录互不干扰 (100 iterations)")
    void multiplePartsDoNotInterfere() {
        // 运行 100 次随机输入测试
        for (int iteration = 1; iteration <= 100; iteration++) {
            // Given - 生成随机的 taskId 和三个不同的 partNumber
            String taskId = generateRandomTaskId();
            int partNumber1 = random.nextInt(100) + 1;
            int partNumber2 = random.nextInt(100) + 101; // 确保不同
            int partNumber3 = random.nextInt(100) + 201; // 确保不同

            UploadPart part1 = createPart(taskId, partNumber1);
            UploadPart part2 = createPart(taskId, partNumber2);
            UploadPart part3 = createPart(taskId, partNumber3);

            String expectedKey = UploadRedisKeys.partsBitmap(taskId);

            // When - 依次记录三个分片
            repository.savePart(part1);
            repository.savePart(part2);
            repository.savePart(part3);

            // Then - 验证三个分片都被正确记录
            Boolean bit1 = redisTemplate.opsForValue().getBit(expectedKey, UploadRedisKeys.getBitOffset(partNumber1));
            Boolean bit2 = redisTemplate.opsForValue().getBit(expectedKey, UploadRedisKeys.getBitOffset(partNumber2));
            Boolean bit3 = redisTemplate.opsForValue().getBit(expectedKey, UploadRedisKeys.getBitOffset(partNumber3));

            assertThat(bit1).as("[Iteration %d] 分片 %d 应被记录", iteration, partNumber1).isTrue();
            assertThat(bit2).as("[Iteration %d] 分片 %d 应被记录", iteration, partNumber2).isTrue();
            assertThat(bit3).as("[Iteration %d] 分片 %d 应被记录", iteration, partNumber3).isTrue();

            // 验证 BITCOUNT 为 3
            Long bitCount = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                connection.bitCount(expectedKey.getBytes())
            );
            
            assertThat(bitCount)
                    .as("[Iteration %d] 应该有 3 个分片被记录", iteration)
                    .isEqualTo(3L);

            // 清理测试数据
            redisTemplate.delete(expectedKey);
        }
    }

    /**
     * Property 1.3: 不同任务的 Bitmap 互不干扰
     * 
     * For any 两个不同的任务，它们的 Bitmap 应该完全独立
     * 
     * **Validates: Requirements 1.2**
     * 
     * Feature: multipart-upload-bitmap-optimization, Property 1: Bitmap 记录正确性
     */
    @Test
    @DisplayName("Property 1.3: 不同任务的 Bitmap 互不干扰 (100 iterations)")
    void differentTasksBitmapsAreIndependent() {
        // 运行 100 次随机输入测试
        for (int iteration = 1; iteration <= 100; iteration++) {
            // Given - 生成两个不同的 taskId
            String taskId1 = generateRandomTaskId();
            String taskId2 = generateRandomTaskId();
            int partNumber = random.nextInt(100) + 1;

            UploadPart part1 = createPart(taskId1, partNumber);
            UploadPart part2 = createPart(taskId2, partNumber);

            String key1 = UploadRedisKeys.partsBitmap(taskId1);
            String key2 = UploadRedisKeys.partsBitmap(taskId2);

            // When - 记录两个分片
            repository.savePart(part1);
            repository.savePart(part2);

            // Then - 验证两个 key 不同
            assertThat(key1)
                    .as("[Iteration %d] 不同任务应该有不同的 Bitmap key", iteration)
                    .isNotEqualTo(key2);

            // 验证两个 Bitmap 都正确记录了分片
            long offset = UploadRedisKeys.getBitOffset(partNumber);
            Boolean bit1 = redisTemplate.opsForValue().getBit(key1, offset);
            Boolean bit2 = redisTemplate.opsForValue().getBit(key2, offset);

            assertThat(bit1).as("[Iteration %d] 任务 1 的分片应被记录", iteration).isTrue();
            assertThat(bit2).as("[Iteration %d] 任务 2 的分片应被记录", iteration).isTrue();

            // 验证两个 Bitmap 的 BITCOUNT 都是 1
            Long count1 = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                connection.bitCount(key1.getBytes())
            );
            Long count2 = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                connection.bitCount(key2.getBytes())
            );

            assertThat(count1).as("[Iteration %d] 任务 1 应该有 1 个分片", iteration).isEqualTo(1L);
            assertThat(count2).as("[Iteration %d] 任务 2 应该有 1 个分片", iteration).isEqualTo(1L);

            // 清理测试数据
            redisTemplate.delete(key1);
            redisTemplate.delete(key2);
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
