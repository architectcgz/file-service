package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.monitoring.BitmapMetrics;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 超大文件降级测试
 * 
 * 测试目标：验证当文件分片数超过配置的最大值时，系统自动降级到数据库模式
 * 
 * 测试场景：
 * 1. 测试超过 10000 分片的场景 - 自动降级到数据库
 * 2. 测试恰好 10000 分片的场景 - 使用 Bitmap 模式
 * 3. 测试 10001 分片的场景 - 自动降级到数据库
 * 4. 测试远超 10000 分片的场景（如 50000）- 自动降级到数据库
 * 5. 验证降级时不使用 Redis
 * 6. 验证降级时记录日志
 * 
 * Validates: Requirements 边界条件 5
 * 
 * @author File Service Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("超大文件降级测试 - 边界条件 5")
class UploadPartRepositoryLargeFileDegradationTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UploadPartMapper uploadPartMapper;

    @Mock
    private BitmapProperties bitmapProperties;

    @Mock
    private BitmapMetrics metrics;

    @Mock
    private UploadTaskRepository uploadTaskRepository;

    @InjectMocks
    private UploadPartRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // Mock Redis operations (lenient because degraded tests won't use Redis)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Mock metrics.recordTiming to actually execute the supplier
        lenient().when(metrics.recordTiming(anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        
        // Default: Bitmap enabled (lenient because some tests override this)
        lenient().when(bitmapProperties.isEnabled()).thenReturn(true);
        
        // Default: maxParts = 10000 (lenient because some tests override this)
        lenient().when(bitmapProperties.getMaxParts()).thenReturn(10000);
    }

    /**
     * 测试场景 1: 超过 10000 分片 - 自动降级到数据库
     * 
     * 预期结果：
     * 1. 不调用 Redis 操作
     * 2. 直接写入数据库
     * 3. 记录降级日志
     */
    @Test
    @DisplayName("测试超过 10000 分片 - 自动降级到数据库")
    void testDegradation_ExceedsMaxParts() {
        // Given - 文件有 15000 个分片（超过 maxParts 10000）
        String taskId = UUID.randomUUID().toString();
        UploadTask largeTask = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("large-file.bin")
                .fileSize(150L * 1024 * 1024 * 1024) // 150GB
                .totalParts(15000) // 超过 maxParts
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(largeTask));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证直接写入数据库，不使用 Redis
        verify(uploadPartMapper, times(1)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setBit(anyString(), anyLong(), anyBoolean());
        
        // 验证插入的数据
        ArgumentCaptor<UploadPartPO> captor = ArgumentCaptor.forClass(UploadPartPO.class);
        verify(uploadPartMapper).insert(captor.capture());
        UploadPartPO insertedPart = captor.getValue();
        
        assertThat(insertedPart.getTaskId()).isEqualTo(taskId);
        assertThat(insertedPart.getPartNumber()).isEqualTo(1);
        assertThat(insertedPart.getEtag()).isEqualTo("test-etag");
    }

    /**
     * 测试场景 2: 恰好 10000 分片 - 使用 Bitmap 模式
     * 
     * 预期结果：
     * 1. 使用 Redis Bitmap
     * 2. 不降级到数据库
     */
    @Test
    @DisplayName("测试恰好 10000 分片 - 使用 Bitmap 模式")
    void testNoDegradation_ExactlyMaxParts() {
        // Given - 文件恰好有 10000 个分片
        String taskId = UUID.randomUUID().toString();
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("exact-file.bin")
                .fileSize(100L * 1024 * 1024 * 1024) // 100GB
                .totalParts(10000) // 恰好等于 maxParts
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(task));
        
        when(valueOperations.setBit(anyString(), anyLong(), anyBoolean()))
                .thenReturn(false);

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证使用 Redis Bitmap，不直接写入数据库
        verify(valueOperations, times(1)).setBit(anyString(), anyLong(), eq(true));
        verify(uploadPartMapper, never()).insert(any(UploadPartPO.class));
    }

    /**
     * 测试场景 3: 10001 分片 - 自动降级到数据库
     * 
     * 预期结果：
     * 1. 不调用 Redis 操作
     * 2. 直接写入数据库
     */
    @Test
    @DisplayName("测试 10001 分片 - 自动降级到数据库")
    void testDegradation_JustOverMaxParts() {
        // Given - 文件有 10001 个分片（刚好超过 maxParts）
        String taskId = UUID.randomUUID().toString();
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("just-over-file.bin")
                .fileSize(100L * 1024 * 1024 * 1024) // 100GB
                .totalParts(10001) // 刚好超过 maxParts
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证直接写入数据库，不使用 Redis
        verify(uploadPartMapper, times(1)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setBit(anyString(), anyLong(), anyBoolean());
    }

    /**
     * 测试场景 4: 远超 10000 分片（50000）- 自动降级到数据库
     * 
     * 预期结果：
     * 1. 不调用 Redis 操作
     * 2. 直接写入数据库
     */
    @Test
    @DisplayName("测试远超 10000 分片（50000）- 自动降级到数据库")
    void testDegradation_FarExceedsMaxParts() {
        // Given - 文件有 50000 个分片（远超 maxParts）
        String taskId = UUID.randomUUID().toString();
        UploadTask hugeTask = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("huge-file.bin")
                .fileSize(500L * 1024 * 1024 * 1024) // 500GB
                .totalParts(50000) // 远超 maxParts
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(hugeTask));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证直接写入数据库，不使用 Redis
        verify(uploadPartMapper, times(1)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setBit(anyString(), anyLong(), anyBoolean());
    }

    /**
     * 测试场景 5: 降级时多个分片连续上传
     * 
     * 预期结果：
     * 1. 所有分片都直接写入数据库
     * 2. 不使用 Redis
     */
    @Test
    @DisplayName("测试降级时多个分片连续上传")
    void testDegradation_MultiplePartsSequentially() {
        // Given - 文件有 20000 个分片
        String taskId = UUID.randomUUID().toString();
        UploadTask largeTask = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("large-file.bin")
                .fileSize(200L * 1024 * 1024 * 1024) // 200GB
                .totalParts(20000)
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(largeTask));

        // When - 连续上传 5 个分片
        for (int i = 1; i <= 5; i++) {
            UploadPart part = UploadPart.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .partNumber(i)
                    .etag("test-etag-" + i)
                    .size(10L * 1024 * 1024)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            
            repository.savePart(part);
        }

        // Then - 验证所有分片都直接写入数据库
        verify(uploadPartMapper, times(5)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setBit(anyString(), anyLong(), anyBoolean());
    }

    /**
     * 测试场景 6: 降级时 Bitmap 功能仍然启用
     * 
     * 预期结果：
     * 1. 即使 Bitmap 启用，也应该降级到数据库
     * 2. 不使用 Redis
     */
    @Test
    @DisplayName("测试降级时 Bitmap 功能仍然启用")
    void testDegradation_BitmapEnabledButDegraded() {
        // Given - Bitmap 启用，但文件分片数超过限制
        lenient().when(bitmapProperties.isEnabled()).thenReturn(true);
        lenient().when(bitmapProperties.getMaxParts()).thenReturn(10000);

        String taskId = UUID.randomUUID().toString();
        UploadTask largeTask = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("large-file.bin")
                .fileSize(150L * 1024 * 1024 * 1024)
                .totalParts(15000)
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(largeTask));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证降级到数据库
        verify(uploadPartMapper, times(1)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
    }

    /**
     * 测试场景 7: 不同 maxParts 配置的降级行为
     * 
     * 预期结果：
     * 1. 根据配置的 maxParts 值决定是否降级
     */
    @Test
    @DisplayName("测试不同 maxParts 配置的降级行为")
    void testDegradation_DifferentMaxPartsConfig() {
        // Given - 配置 maxParts = 5000
        when(bitmapProperties.getMaxParts()).thenReturn(5000);

        String taskId = UUID.randomUUID().toString();
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("medium-file.bin")
                .fileSize(60L * 1024 * 1024 * 1024)
                .totalParts(6000) // 超过配置的 5000
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证降级到数据库
        verify(uploadPartMapper, times(1)).insert(any(UploadPartPO.class));
        verify(redisTemplate, never()).opsForValue();
    }

    /**
     * 测试场景 8: 小于 maxParts 的文件不降级
     * 
     * 预期结果：
     * 1. 使用 Redis Bitmap
     * 2. 不降级到数据库
     */
    @Test
    @DisplayName("测试小于 maxParts 的文件不降级")
    void testNoDegradation_BelowMaxParts() {
        // Given - 文件有 5000 个分片（小于 maxParts 10000）
        String taskId = UUID.randomUUID().toString();
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .userId("test-user")
                .fileName("normal-file.bin")
                .fileSize(50L * 1024 * 1024 * 1024)
                .totalParts(5000) // 小于 maxParts
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        when(uploadTaskRepository.findById(taskId))
                .thenReturn(Optional.of(task));
        
        when(valueOperations.setBit(anyString(), anyLong(), anyBoolean()))
                .thenReturn(false);

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .partNumber(1)
                .etag("test-etag")
                .size(10L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When - 保存分片
        repository.savePart(part);

        // Then - 验证使用 Redis Bitmap
        verify(valueOperations, times(1)).setBit(anyString(), anyLong(), eq(true));
        verify(uploadPartMapper, never()).insert(any(UploadPartPO.class));
    }
}
