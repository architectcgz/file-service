package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.exception.AccessDeniedException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadAbortCommandService 单元测试")
class MultipartUploadAbortCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;

    private MultipartUploadAbortCommandService multipartUploadAbortCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        multipartUploadAbortCommandService =
                new MultipartUploadAbortCommandService(
                        new MultipartUploadTaskValidator(),
                        new MultipartUploadStorageService(s3StorageService),
                        uploadTaskQueryService,
                        uploadTaskCommandService
                );
    }

    @Test
    @DisplayName("中止上传时 appId 不匹配应拒绝")
    void abortUpload_shouldRejectWhenAppIdMismatch() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .uploadId("upload-001")
                .storagePath("blog/files/archive.zip")
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> multipartUploadAbortCommandService.abortUpload("im", "task-001", "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
