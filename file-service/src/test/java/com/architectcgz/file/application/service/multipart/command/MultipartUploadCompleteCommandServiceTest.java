package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.multipart.factory.MultipartUploadObjectFactory;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.command.UploadPartSyncCommandService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartCompletionQueryService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadCompleteCommandService 单元测试")
class MultipartUploadCompleteCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;
    @Mock
    private UploadPartStateQueryService uploadPartStateQueryService;
    @Mock
    private UploadPartSyncCommandService uploadPartSyncCommandService;
    @Mock
    private UploadPartCompletionQueryService uploadPartCompletionQueryService;

    private MultipartUploadCompleteCommandService multipartUploadCompleteCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        multipartUploadCompleteCommandService = new MultipartUploadCompleteCommandService(
                new MultipartUploadTaskValidator(),
                new MultipartUploadStorageService(s3StorageService),
                new MultipartUploadObjectFactory(),
                uploadTaskQueryService,
                uploadPartStateQueryService,
                uploadPartSyncCommandService,
                uploadPartCompletionQueryService,
                storageObjectRepository,
                uploadTransactionHelper
        );
    }

    @Test
    @DisplayName("完成分片上传时应将对象写入 public bucket")
    void completeUpload_shouldPersistMetadataInPublicBucket() {
        UploadTask task = buildUploadingTask("hash-456", 2);
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(uploadPartStateQueryService.countCompletedParts("task-001")).thenReturn(2);
        when(uploadPartCompletionQueryService.loadPersistedCompletedParts("task-001")).thenReturn(List.of(
                CompletedPart.builder().partNumber(1).eTag("etag-1").build(),
                CompletedPart.builder().partNumber(2).eTag("etag-2").build()
        ));
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-456", "public-bucket"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<StorageObject> storageCaptor = ArgumentCaptor.forClass(StorageObject.class);
        ArgumentCaptor<FileRecord> recordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveCompletedUpload(any(UploadTask.class), storageCaptor.capture(), recordCaptor.capture());

        String fileId = multipartUploadCompleteCommandService.completeUpload("blog", "task-001", "user-123");

        assertThat(fileId).isEqualTo(recordCaptor.getValue().getId());
        assertThat(storageCaptor.getValue().getBucketName()).isEqualTo("public-bucket");
        assertThat(recordCaptor.getValue().getStorageObjectId()).isEqualTo(storageCaptor.getValue().getId());
        verify(uploadPartSyncCommandService).syncAllParts(eq("task-001"), eq(List.of()));
        verify(s3StorageService).completeMultipartUpload(eq("blog/files/archive.zip"), eq("upload-001"), any(), eq("public-bucket"));
    }

    @Test
    @DisplayName("完成分片上传时缺少 fileHash 应返回稳定错误码")
    void completeUpload_shouldReturnStableErrorCodeWhenFileHashMissing() {
        UploadTask task = buildUploadingTask(" ", 1);
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(uploadPartStateQueryService.countCompletedParts("task-001")).thenReturn(1);
        when(uploadPartCompletionQueryService.loadPersistedCompletedParts("task-001")).thenReturn(List.of(
                CompletedPart.builder().partNumber(1).eTag("etag-1").build()
        ));

        assertThatThrownBy(() -> multipartUploadCompleteCommandService.completeUpload("blog", "task-001", "user-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传任务缺少 fileHash，无法建立存储对象")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(FileServiceErrorCodes.UPLOAD_TASK_FILE_HASH_MISSING));
    }

    private UploadTask buildUploadingTask(String fileHash, int totalParts) {
        return UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .fileName("archive.zip")
                .fileSize(2048L)
                .fileHash(fileHash)
                .contentType("application/zip")
                .storagePath("blog/files/archive.zip")
                .uploadId("upload-001")
                .totalParts(totalParts)
                .chunkSize(1024)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }

}
