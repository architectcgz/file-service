package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.monitoring.BitmapMetrics;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UploadPartRepository 参数校验单元测试
 * 
 * 测试目标：验证 savePart() 方法的参数校验逻辑
 * 
 * 测试场景：
 * 1. 测试无效分片编号（< 1）
 * 2. 测试无效分片编号（> totalParts）
 * 3. 测试无效 taskId（null）
 * 4. 测试无效 taskId（空字符串）
 * 5. 测试无效 taskId（不存在的任务）
 * 6. 测试 null 分片对象
 * 7. 测试 null 分片编号
 * 
 * Validates: Requirements 边界条件 7
 * 
 * @author File Service Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("参数校验单元测试 - 边界条件 7")
class UploadPartRepositoryParameterValidationTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

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

    private String validTaskId;
    private UploadTask validTask;

    @BeforeEach
    void setUp() {
        validTaskId = UUID.randomUUID().toString();
        validTask = UploadTask.builder()
                .id(validTaskId)
                .userId("test-user")
                .fileName("test-file.txt")
                .fileSize(100L * 1024 * 1024) // 100MB
                .totalParts(100)
                .uploadId("test-upload-id")
                .status(UploadTaskStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Mock metrics.recordTiming to actually execute the supplier
        when(metrics.recordTiming(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    /**
     * 测试场景 1: 分片编号小于 1
     * 
     * 预期结果：抛出 IllegalArgumentException，提示分片编号必须大于0
     */
    @Test
    @DisplayName("测试无效分片编号 - partNumber < 1")
    void testInvalidPartNumber_LessThanOne() {
        // Given - 分片编号为 0
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(validTaskId)
                .partNumber(0)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号必须大于0")
                .hasMessageContaining("partNumber=0");
    }

    /**
     * 测试场景 2: 分片编号为负数
     * 
     * 预期结果：抛出 IllegalArgumentException
     */
    @Test
    @DisplayName("测试无效分片编号 - partNumber < 0")
    void testInvalidPartNumber_Negative() {
        // Given - 分片编号为 -1
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(validTaskId)
                .partNumber(-1)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号必须大于0")
                .hasMessageContaining("partNumber=-1");
    }

    /**
     * 测试场景 3: 分片编号超过总分片数
     * 
     * 预期结果：抛出 IllegalArgumentException，提示分片编号超出范围
     */
    @Test
    @DisplayName("测试无效分片编号 - partNumber > totalParts")
    void testInvalidPartNumber_ExceedsTotalParts() {
        // Given - 任务有 100 个分片，但分片编号为 101
        when(uploadTaskRepository.findById(validTaskId))
                .thenReturn(Optional.of(validTask));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(validTaskId)
                .partNumber(101) // 超过 totalParts (100)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号超出范围")
                .hasMessageContaining("partNumber=101")
                .hasMessageContaining("有效范围=[1, 100]");
    }

    /**
     * 测试场景 4: 分片编号远超总分片数
     * 
     * 预期结果：抛出 IllegalArgumentException
     */
    @Test
    @DisplayName("测试无效分片编号 - partNumber >> totalParts")
    void testInvalidPartNumber_FarExceedsTotalParts() {
        // Given - 任务有 100 个分片，但分片编号为 10000
        when(uploadTaskRepository.findById(validTaskId))
                .thenReturn(Optional.of(validTask));

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(validTaskId)
                .partNumber(10000) // 远超 totalParts (100)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号超出范围")
                .hasMessageContaining("partNumber=10000")
                .hasMessageContaining("有效范围=[1, 100]");
    }

    /**
     * 测试场景 5: taskId 为 null
     * 
     * 预期结果：抛出 IllegalArgumentException，提示任务ID不能为空
     */
    @Test
    @DisplayName("测试无效 taskId - null")
    void testInvalidTaskId_Null() {
        // Given - taskId 为 null
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(null)
                .partNumber(1)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务ID不能为空");
    }

    /**
     * 测试场景 6: taskId 为空字符串
     * 
     * 预期结果：抛出 IllegalArgumentException
     */
    @Test
    @DisplayName("测试无效 taskId - 空字符串")
    void testInvalidTaskId_EmptyString() {
        // Given - taskId 为空字符串
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId("")
                .partNumber(1)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务ID不能为空");
    }

    /**
     * 测试场景 7: taskId 为空白字符串
     * 
     * 预期结果：抛出 IllegalArgumentException
     */
    @Test
    @DisplayName("测试无效 taskId - 空白字符串")
    void testInvalidTaskId_BlankString() {
        // Given - taskId 为空白字符串
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId("   ")
                .partNumber(1)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务ID不能为空");
    }

    /**
     * 测试场景 8: taskId 不存在
     * 
     * 预期结果：抛出 IllegalArgumentException，提示上传任务不存在
     */
    @Test
    @DisplayName("测试无效 taskId - 任务不存在")
    void testInvalidTaskId_TaskNotFound() {
        // Given - taskId 不存在
        String nonExistentTaskId = UUID.randomUUID().toString();
        when(uploadTaskRepository.findById(nonExistentTaskId))
                .thenReturn(Optional.empty());

        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(nonExistentTaskId)
                .partNumber(1)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上传任务不存在")
                .hasMessageContaining("taskId=" + nonExistentTaskId);
    }

    /**
     * 测试场景 9: 分片对象为 null
     * 
     * 预期结果：抛出 IllegalArgumentException，提示分片信息不能为空
     */
    @Test
    @DisplayName("测试 null 分片对象")
    void testNullPart() {
        // Given - 分片对象为 null
        UploadPart part = null;

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片信息不能为空");
    }

    /**
     * 测试场景 10: 分片编号为 null
     * 
     * 预期结果：抛出 IllegalArgumentException，提示分片编号不能为空
     */
    @Test
    @DisplayName("测试 null 分片编号")
    void testNullPartNumber() {
        // Given - 分片编号为 null
        UploadPart part = UploadPart.builder()
                .id(UUID.randomUUID().toString())
                .taskId(validTaskId)
                .partNumber(null)
                .etag("test-etag")
                .size(5L * 1024 * 1024)
                .uploadedAt(LocalDateTime.now())
                .build();

        // When & Then - 应该抛出 IllegalArgumentException
        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号不能为空");
    }
}
