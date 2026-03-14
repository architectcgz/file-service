package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.infrastructure.monitoring.BitmapMetrics;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.mapper.UploadTaskMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadPartRepository 错误码测试")
class UploadPartRepositoryErrorCodeTest {

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
        lenient().when(metrics.recordTiming(anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Test
    @DisplayName("savePart 在数据库写入失败时返回稳定错误码")
    void testSavePartUsesStableErrorCodeWhenDatabaseWriteFails() {
        UploadPart part = UploadPart.builder()
                .id("part-001")
                .taskId("task-001")
                .partNumber(1)
                .etag("etag-001")
                .size(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();
        UploadTaskPO task = createTask("task-001", 3);

        when(uploadTaskMapper.selectById("task-001")).thenReturn(task);
        when(bitmapProperties.getMaxParts()).thenReturn(100);
        when(bitmapProperties.isEnabled()).thenReturn(false);
        doThrow(new RuntimeException("db down")).when(uploadPartMapper).upsert(any());

        assertThatThrownBy(() -> repository.savePart(part))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(FileServiceErrorCodes.PART_DB_WRITE_FAILED))
                .hasMessage(String.format(FileServiceErrorMessages.PART_DB_WRITE_FAILED, "task-001", 1));
    }

    @Test
    @DisplayName("syncAllPartsToDatabase 在批量插入失败时返回稳定错误码")
    void testSyncAllPartsUsesStableErrorCodeWhenBatchInsertFails() {
        UploadPart part = UploadPart.builder()
                .id("part-001")
                .taskId("task-001")
                .partNumber(1)
                .etag("etag-001")
                .size(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();

        doCallRealMethod().when(uploadPartMapper).batchInsert(any());
        doThrow(new RuntimeException("batch fail")).when(uploadPartMapper).doBatchInsert(any());

        assertThatThrownBy(() -> repository.syncAllPartsToDatabase("task-001", List.of(part)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(FileServiceErrorCodes.PART_BATCH_INSERT_FAILED))
                .hasMessage(String.format(FileServiceErrorMessages.PART_BATCH_INSERT_FAILED, 1));
    }

    @Test
    @DisplayName("syncAllPartsToDatabase 在清理 bitmap 失败时返回稳定错误码")
    void testSyncAllPartsUsesStableErrorCodeWhenBitmapCleanupFails() {
        UploadPart part = UploadPart.builder()
                .id("part-001")
                .taskId("task-001")
                .partNumber(1)
                .etag("etag-001")
                .size(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(bitmapProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> repository.syncAllPartsToDatabase("task-001", List.of(part)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(FileServiceErrorCodes.PART_SYNC_FAILED))
                .hasMessage(String.format(FileServiceErrorMessages.PART_SYNC_FAILED, "task-001"));
    }

    private UploadTaskPO createTask(String taskId, int totalParts) {
        UploadTaskPO task = new UploadTaskPO();
        task.setId(taskId);
        task.setTotalParts(totalParts);
        return task;
    }
}
