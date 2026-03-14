package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.assembler.DirectUploadPartResponseAssembler;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.domain.model.AccessLevel;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadProgressQueryService 单元测试")
class DirectUploadProgressQueryServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;

    private DirectUploadProgressQueryService directUploadProgressQueryService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        DirectUploadTaskValidator directUploadTaskValidator = new DirectUploadTaskValidator(uploadTaskCommandService);
        directUploadProgressQueryService = new DirectUploadProgressQueryService(
                directUploadTaskValidator,
                new DirectUploadStorageService(s3StorageService),
                new DirectUploadPartResponseAssembler(),
                uploadTaskQueryService
        );
    }

    @Test
    @DisplayName("查询直传进度时应返回 S3 中已完成的分片信息")
    void getUploadProgress_shouldReturnAuthoritativePartsFromS3() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .fileSize(1024L)
                .storagePath("blog/files/report.pdf")
                .uploadId("upload-001")
                .totalParts(4)
                .chunkSize(256)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(s3StorageService.listUploadedPartsWithETag("blog/files/report.pdf", "upload-001", "public-bucket"))
                .thenReturn(List.of(
                        new S3StorageService.PartInfo(1, "etag-1"),
                        new S3StorageService.PartInfo(3, "etag-3")
                ));

        DirectUploadProgressResponse response =
                directUploadProgressQueryService.getUploadProgress("blog", "task-001", "user-123");

        assertThat(response.getCompletedParts()).isEqualTo(2);
        assertThat(response.getCompletedPartNumbers()).containsExactly(1, 3);
        assertThat(response.getPercentage()).isEqualTo(50);
        assertThat(response.getCompletedPartInfos()).hasSize(2);
    }
}
