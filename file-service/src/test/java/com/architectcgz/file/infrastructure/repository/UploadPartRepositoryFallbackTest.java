package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.infrastructure.monitoring.BitmapMetrics;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.mapper.UploadTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadPartRepository 回退测试")
class UploadPartRepositoryFallbackTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private UploadPartMapper uploadPartMapper;

    @Mock
    private BitmapProperties bitmapProperties;

    @Mock
    private BitmapMetrics metrics;

    @Mock
    private UploadTaskMapper uploadTaskMapper;

    @InjectMocks
    private UploadPartRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        when(metrics.recordTiming(anyString(), any()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> supplier = invocation.getArgument(1);
                return supplier.get();
            });
    }

    @Test
    @DisplayName("countCompletedParts 在 bitmap key 不存在时回退数据库")
    void countCompletedParts_shouldFallbackToDatabaseWhenBitmapKeyMissing() {
        String taskId = "task-missing-bitmap";
        when(bitmapProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.hasKey("upload:task:" + taskId + ":parts")).thenReturn(false);
        when(uploadPartMapper.countByTaskId(taskId)).thenReturn(7);

        int completedParts = repository.countCompletedParts(taskId);

        assertThat(completedParts).isEqualTo(7);
        verify(metrics).recordCacheMiss();
        verify(uploadPartMapper).countByTaskId(taskId);
        verify(redisTemplate, never()).execute(any(RedisCallback.class));
    }
}
