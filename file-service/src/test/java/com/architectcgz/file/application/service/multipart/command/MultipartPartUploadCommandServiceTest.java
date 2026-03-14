package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.UploadPartTransactionHelper;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartPartUploadCommandService 单元测试")
class MultipartPartUploadCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private MultipartProperties multipartProperties;
    @Mock
    private UploadPartTransactionHelper uploadPartTransactionHelper;
    @Mock
    private UploadPartStateQueryService uploadPartStateQueryService;

    private MultipartPartUploadCommandService multipartPartUploadCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        multipartPartUploadCommandService = new MultipartPartUploadCommandService(
                new MultipartUploadTaskValidator(),
                new MultipartUploadStorageService(s3StorageService),
                uploadTaskQueryService,
                uploadTaskCommandService,
                uploadPartStateQueryService,
                redisTemplate,
                multipartProperties,
                uploadPartTransactionHelper
        );
    }

    @Test
    @DisplayName("上传分片时 appId 不匹配应拒绝")
    void uploadPart_shouldRejectWhenAppIdMismatch() {
        UploadTask task = buildUploadingTask();
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> multipartPartUploadCommandService.uploadPart("im", "task-001", 1, "data".getBytes(), "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("上传分片时非法分片号应返回稳定错误码")
    void uploadPart_shouldReturnStableErrorCodeWhenPartNumberInvalid() {
        UploadTask task = buildUploadingTask();
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> multipartPartUploadCommandService.uploadPart("blog", "task-001", 3, "data".getBytes(), "user-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分片号无效: 3")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(FileServiceErrorCodes.PART_NUMBER_INVALID));
    }

    private UploadTask buildUploadingTask() {
        return UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .uploadId("upload-001")
                .storagePath("blog/files/archive.zip")
                .status(UploadTaskStatus.UPLOADING)
                .totalParts(2)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }
}
