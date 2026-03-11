package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadService 单元测试")
class DirectUploadServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private UploadPartRepository uploadPartRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private MultipartProperties multipartProperties;
    @Mock
    private AccessProperties accessProperties;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;

    @InjectMocks
    private DirectUploadService directUploadService;

    private DirectUploadInitRequest initRequest;

    @BeforeEach
    void setUp() {
        initRequest = new DirectUploadInitRequest();
        initRequest.setFileName("report.pdf");
        initRequest.setFileSize(1024L);
        initRequest.setContentType("application/pdf");
        initRequest.setFileHash("hash-123");
    }

    @Test
    @DisplayName("秒传命中时应按 public bucket 去重并返回公开地址")
    void initDirectUpload_shouldUsePublicBucketForInstantUpload() {
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .fileHash("hash-123")
                .storagePath("blog/files/report.pdf")
                .bucketName("public-bucket")
                .fileSize(1024L)
                .contentType("application/pdf")
                .referenceCount(2)
                .build();

        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.of(storageObject));
        when(s3StorageService.getPublicUrl("public-bucket", "blog/files/report.pdf"))
                .thenReturn("https://cdn.example.com/blog/files/report.pdf");
        doNothing().when(tenantDomainService).checkQuota("blog", 1024L);
        doNothing().when(fileTypeValidator).validateFile("report.pdf", "application/pdf", 1024L);

        ArgumentCaptor<FileRecord> fileRecordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveInstantUpload(anyString(), fileRecordCaptor.capture(), anyLong());

        DirectUploadInitResponse response = directUploadService.initDirectUpload("blog", initRequest, "user-123");

        assertThat(response.getIsInstantUpload()).isTrue();
        assertThat(response.getFileUrl()).isEqualTo("https://cdn.example.com/blog/files/report.pdf");
        assertThat(fileRecordCaptor.getValue().getStorageObjectId()).isEqualTo("storage-001");
        verify(storageObjectRepository).findByFileHashAndBucket("blog", "hash-123", "public-bucket");
        verify(uploadTransactionHelper).saveInstantUpload("storage-001", fileRecordCaptor.getValue(), 1024L);
    }

    @Test
    @DisplayName("完成直传上传时应将对象写入 public bucket")
    void completeDirectUpload_shouldPersistMetadataInPublicBucket() {
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .fileName("report.pdf")
                .fileSize(1024L)
                .fileHash("hash-123")
                .contentType("application/pdf")
                .storagePath("blog/files/report.pdf")
                .uploadId("upload-001")
                .totalParts(2)
                .chunkSize(512)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        DirectUploadCompleteRequest.PartInfo part1 = new DirectUploadCompleteRequest.PartInfo();
        part1.setPartNumber(1);
        part1.setEtag("etag-1");
        DirectUploadCompleteRequest.PartInfo part2 = new DirectUploadCompleteRequest.PartInfo();
        part2.setPartNumber(2);
        part2.setEtag("etag-2");
        request.setParts(List.of(part1, part2));

        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<StorageObject> storageCaptor = ArgumentCaptor.forClass(StorageObject.class);
        ArgumentCaptor<FileRecord> recordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveCompletedUpload(any(UploadTask.class), storageCaptor.capture(), recordCaptor.capture());

        String fileId = directUploadService.completeDirectUpload(request, "user-123");

        assertThat(fileId).isEqualTo(recordCaptor.getValue().getId());
        assertThat(storageCaptor.getValue().getBucketName()).isEqualTo("public-bucket");
        assertThat(recordCaptor.getValue().getStorageObjectId()).isEqualTo(storageCaptor.getValue().getId());
        verify(s3StorageService).completeMultipartUpload(eq("blog/files/report.pdf"), eq("upload-001"), any(), eq("public-bucket"));
    }
}
