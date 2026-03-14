package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.direct.factory.DirectUploadObjectFactory;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.command.UploadPartSyncCommandService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.AccessDeniedException;
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
@DisplayName("DirectUploadCompleteCommandService 单元测试")
class DirectUploadCompleteCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;
    @Mock
    private UploadPartSyncCommandService uploadPartSyncCommandService;

    private DirectUploadCompleteCommandService directUploadCompleteCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        DirectUploadTaskValidator directUploadTaskValidator = new DirectUploadTaskValidator(uploadTaskCommandService);
        directUploadCompleteCommandService = new DirectUploadCompleteCommandService(
                directUploadTaskValidator,
                new DirectUploadStorageService(s3StorageService),
                new DirectUploadObjectFactory(),
                uploadTaskQueryService,
                uploadPartSyncCommandService,
                storageObjectRepository,
                uploadTransactionHelper
        );
    }

    @Test
    @DisplayName("完成直传上传时应将对象写入 public bucket")
    void completeDirectUpload_shouldPersistMetadataInPublicBucket() {
        UploadTask task = buildUploadingTask("hash-123", 2);
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        request.setParts(List.of(part(1, "etag-1"), part(2, "etag-2")));

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.empty());
        when(s3StorageService.listUploadedPartsWithETag("blog/files/report.pdf", "upload-001", "public-bucket"))
                .thenReturn(List.of(
                        new S3StorageService.PartInfo(1, "etag-1"),
                        new S3StorageService.PartInfo(2, "etag-2")
                ));

        ArgumentCaptor<StorageObject> storageCaptor = ArgumentCaptor.forClass(StorageObject.class);
        ArgumentCaptor<FileRecord> recordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveCompletedUpload(any(UploadTask.class), storageCaptor.capture(), recordCaptor.capture());

        String fileId = directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123");

        assertThat(fileId).isEqualTo(recordCaptor.getValue().getId());
        assertThat(storageCaptor.getValue().getBucketName()).isEqualTo("public-bucket");
        assertThat(recordCaptor.getValue().getStorageObjectId()).isEqualTo(storageCaptor.getValue().getId());
        verify(uploadPartSyncCommandService).syncAllParts(eq("task-001"), any());
        verify(s3StorageService).completeMultipartUpload(eq("blog/files/report.pdf"), eq("upload-001"), any(), eq("public-bucket"));
    }

    @Test
    @DisplayName("完成直传上传时即使客户端未传 parts 也应基于 S3 已上传分片完成")
    void completeDirectUpload_shouldUseAuthoritativeS3PartsWhenClientPartsMissing() {
        UploadTask task = buildUploadingTask("hash-123", 2);
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        request.setParts(null);

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.empty());
        when(s3StorageService.listUploadedPartsWithETag("blog/files/report.pdf", "upload-001", "public-bucket"))
                .thenReturn(List.of(
                        new S3StorageService.PartInfo(1, "etag-1"),
                        new S3StorageService.PartInfo(2, "etag-2")
                ));
        doNothing().when(uploadTransactionHelper)
                .saveCompletedUpload(any(UploadTask.class), any(StorageObject.class), any(FileRecord.class));

        String fileId = directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123");

        assertThat(fileId).isNotBlank();
        verify(s3StorageService).completeMultipartUpload(eq("blog/files/report.pdf"), eq("upload-001"), any(), eq("public-bucket"));
    }

    @Test
    @DisplayName("完成直传上传时客户端 ETag 与 S3 不一致应拒绝")
    void completeDirectUpload_shouldRejectWhenClientEtagMismatch() {
        UploadTask task = buildUploadingTask("hash-123", 1);
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        request.setParts(List.of(part(1, "client-etag")));

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(s3StorageService.listUploadedPartsWithETag("blog/files/report.pdf", "upload-001", "public-bucket"))
                .thenReturn(List.of(new S3StorageService.PartInfo(1, "s3-etag")));

        assertThatThrownBy(() -> directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分片ETag不匹配: 1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(FileServiceErrorCodes.PART_ETAG_MISMATCH));
    }

    @Test
    @DisplayName("完成直传上传时 appId 不匹配应拒绝")
    void completeDirectUpload_shouldRejectWhenAppIdMismatch() {
        UploadTask task = buildUploadingTask("hash-123", 1);
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setParts(List.of(part(1, "etag-1")));

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> directUploadCompleteCommandService.completeDirectUpload("im", request, "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("完成直传上传时缺少 fileHash 应返回稳定错误码")
    void completeDirectUpload_shouldReturnStableErrorCodeWhenFileHashMissing() {
        UploadTask task = buildUploadingTask(" ", 1);
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        request.setParts(List.of());

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(s3StorageService.listUploadedPartsWithETag("blog/files/report.pdf", "upload-001", "public-bucket"))
                .thenReturn(List.of(new S3StorageService.PartInfo(1, "etag-1")));

        assertThatThrownBy(() -> directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123"))
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
                .fileName("report.pdf")
                .fileSize(1024L)
                .fileHash(fileHash)
                .contentType("application/pdf")
                .storagePath("blog/files/report.pdf")
                .uploadId("upload-001")
                .totalParts(totalParts)
                .chunkSize(512)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    private DirectUploadCompleteRequest.PartInfo part(int partNumber, String etag) {
        DirectUploadCompleteRequest.PartInfo part = new DirectUploadCompleteRequest.PartInfo();
        part.setPartNumber(partNumber);
        part.setEtag(etag);
        return part;
    }
}
