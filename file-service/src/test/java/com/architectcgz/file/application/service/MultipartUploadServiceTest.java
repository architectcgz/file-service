package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.common.exception.AccessDeniedException;
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
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

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
@DisplayName("MultipartUploadService 单元测试")
class MultipartUploadServiceTest {

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
    private FileTypeValidator fileTypeValidator;
    @Mock
    private UploadPartMapper uploadPartMapper;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private UploadPartTransactionHelper uploadPartTransactionHelper;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;

    @InjectMocks
    private MultipartUploadService multipartUploadService;

    private InitUploadRequest initRequest;

    @BeforeEach
    void setUp() {
        initRequest = new InitUploadRequest();
        initRequest.setFileName("archive.zip");
        initRequest.setFileSize(2048L);
        initRequest.setContentType("application/zip");
        initRequest.setFileHash("hash-456");
    }

    @Test
    @DisplayName("初始化分片上传时应在 public bucket 创建任务")
    void initUpload_shouldCreateMultipartUploadInPublicBucket() {
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(s3StorageService.createMultipartUpload(any(), any(), eq("public-bucket"))).thenReturn("upload-001");
        when(multipartProperties.getChunkSize()).thenReturn(1024);
        when(multipartProperties.getMaxParts()).thenReturn(10);
        when(multipartProperties.getTaskExpireHours()).thenReturn(24);
        when(uploadTaskRepository.findByUserIdAndFileHash("blog", "user-123", "hash-456"))
                .thenReturn(Optional.empty());
        doNothing().when(tenantDomainService).checkQuota("blog", 2048L);
        doNothing().when(fileTypeValidator).validateFile("archive.zip", "application/zip", 2048L);

        multipartUploadService.initUpload("blog", initRequest, "user-123");

        verify(tenantDomainService).checkQuota("blog", 2048L);
        verify(s3StorageService).createMultipartUpload(any(), eq("application/zip"), eq("public-bucket"));
    }

    @Test
    @DisplayName("完成分片上传时应将对象写入 public bucket")
    void completeUpload_shouldPersistMetadataInPublicBucket() {
        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .fileName("archive.zip")
                .fileSize(2048L)
                .fileHash("hash-456")
                .contentType("application/zip")
                .storagePath("blog/files/archive.zip")
                .uploadId("upload-001")
                .totalParts(2)
                .chunkSize(1024)
                .status(UploadTaskStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        UploadPartPO part1 = new UploadPartPO();
        part1.setId("part-1");
        part1.setTaskId("task-001");
        part1.setPartNumber(1);
        part1.setEtag("etag-1");
        UploadPartPO part2 = new UploadPartPO();
        part2.setId("part-2");
        part2.setTaskId("task-001");
        part2.setPartNumber(2);
        part2.setEtag("etag-2");

        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));
        when(uploadPartRepository.countCompletedParts("task-001")).thenReturn(2);
        when(uploadPartRepository.findCompletedPartNumbers("task-001")).thenReturn(List.of(1, 2));
        when(uploadPartMapper.selectByTaskId("task-001")).thenReturn(List.of(part1, part2));
        doNothing().when(uploadPartRepository).syncAllPartsToDatabase(eq("task-001"), any());
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-456", "public-bucket"))
                .thenReturn(Optional.empty());

        ArgumentCaptor<StorageObject> storageCaptor = ArgumentCaptor.forClass(StorageObject.class);
        ArgumentCaptor<FileRecord> recordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveCompletedUpload(any(UploadTask.class), storageCaptor.capture(), recordCaptor.capture());

        String fileId = multipartUploadService.completeUpload("blog", "task-001", "user-123");

        assertThat(fileId).isEqualTo(recordCaptor.getValue().getId());
        assertThat(storageCaptor.getValue().getBucketName()).isEqualTo("public-bucket");
        assertThat(recordCaptor.getValue().getStorageObjectId()).isEqualTo(storageCaptor.getValue().getId());
        verify(s3StorageService).completeMultipartUpload(eq("blog/files/archive.zip"), eq("upload-001"), any(), eq("public-bucket"));
    }

    @Test
    @DisplayName("上传分片时 appId 不匹配应拒绝")
    void uploadPart_shouldRejectWhenAppIdMismatch() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .uploadId("upload-001")
                .storagePath("blog/files/archive.zip")
                .status(UploadTaskStatus.UPLOADING)
                .totalParts(2)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> multipartUploadService.uploadPart("im", "task-001", 1, "data".getBytes(), "user-123"))
                .isInstanceOf(AccessDeniedException.class);
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

        assertThatThrownBy(() -> multipartUploadService.abortUpload("im", "task-001", "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
