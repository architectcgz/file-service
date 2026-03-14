package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadProgressQueryService 单元测试")
class MultipartUploadProgressQueryServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private UploadPartStateQueryService uploadPartStateQueryService;

    private MultipartUploadProgressQueryService multipartUploadProgressQueryService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        multipartUploadProgressQueryService = new MultipartUploadProgressQueryService(
                new MultipartUploadTaskValidator(),
                uploadTaskQueryService,
                uploadPartStateQueryService
        );
    }

    @Test
    @DisplayName("查询分片上传进度时应基于分片状态服务返回已完成数")
    void getProgress_shouldUseUploadPartStateQueryService() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .fileSize(1024L)
                .totalParts(4)
                .chunkSize(256)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(uploadPartStateQueryService.countCompletedParts("task-001")).thenReturn(2);

        UploadProgressResponse response = multipartUploadProgressQueryService.getProgress("blog", "task-001", "user-123");

        assertThat(response.getCompletedParts()).isEqualTo(2);
        assertThat(response.getUploadedBytes()).isEqualTo(512L);
        assertThat(response.getPercentage()).isEqualTo(50);
    }
}
