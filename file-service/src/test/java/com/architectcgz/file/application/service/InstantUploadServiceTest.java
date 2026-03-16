package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InstantUploadService 单元测试")
class InstantUploadServiceTest {

    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private InstantUploadService instantUploadService;

    private InstantUploadCheckRequest request;
    private StorageObject storageObject;

    @BeforeEach
    void setUp() {
        request = InstantUploadCheckRequest.builder()
                .fileHash("hash-123")
                .fileSize(1024L)
                .fileName("test.jpg")
                .contentType("image/jpeg")
                .build();

        storageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .fileHash("hash-123")
                .hashAlgorithm("MD5")
                .storagePath("blog/files/test.jpg")
                .bucketName("public-bucket")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .referenceCount(1)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    @Test
    @DisplayName("未命中秒传时应按 public bucket 查询")
    void checkInstantUpload_shouldRequireUploadWhenBucketScopedObjectMissing() {
        when(storageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(fileRecordRepository.findByUserIdAndFileHash("blog", "user-1", "hash-123"))
                .thenReturn(Optional.empty());
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.empty());

        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, "user-1");

        assertThat(response.getInstantUpload()).isFalse();
        assertThat(response.getNeedUpload()).isTrue();
        verify(storageObjectRepository).findByFileHashAndBucket("blog", "hash-123", "public-bucket");
    }

    @Test
    @DisplayName("用户已有文件时应返回公开访问地址")
    void checkInstantUpload_shouldReturnExistingUserFileUrl() {
        FileRecord fileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .userId("user-1")
                .storageObjectId("storage-001")
                .storagePath("blog/files/test.jpg")
                .fileHash("hash-123")
                .status(FileStatus.COMPLETED)
                .build();

        when(fileRecordRepository.findByUserIdAndFileHash("blog", "user-1", "hash-123"))
                .thenReturn(Optional.of(fileRecord));
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(storageObject));
        when(storageService.getPublicUrl("public-bucket", "blog/files/test.jpg"))
                .thenReturn("https://cdn.example.com/blog/files/test.jpg");

        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, "user-1");

        assertThat(response.getInstantUpload()).isTrue();
        assertThat(response.getFileId()).isEqualTo("file-001");
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/blog/files/test.jpg");
    }

    @Test
    @DisplayName("命中共享对象时应创建新的 FileRecord")
    void checkInstantUpload_shouldCreateNewFileRecordForSharedStorageObject() {
        when(storageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(fileRecordRepository.findByUserIdAndFileHash("blog", "user-1", "hash-123"))
                .thenReturn(Optional.empty());
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.of(storageObject));
        when(storageService.getPublicUrl("public-bucket", "blog/files/test.jpg"))
                .thenReturn("https://cdn.example.com/blog/files/test.jpg");

        ArgumentCaptor<FileRecord> fileRecordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        when(fileRecordRepository.save(any(FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, "user-1");

        assertThat(response.getInstantUpload()).isTrue();
        assertThat(response.getNeedUpload()).isFalse();
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/blog/files/test.jpg");
        verify(storageObjectRepository).incrementReferenceCount("storage-001");
        verify(fileRecordRepository).save(fileRecordCaptor.capture());
        assertThat(fileRecordCaptor.getValue().getStorageObjectId()).isEqualTo("storage-001");
        assertThat(fileRecordCaptor.getValue().getOriginalFilename()).isEqualTo("test.jpg");
        verify(storageObjectRepository, never()).findByFileHash("blog", "hash-123");
    }
}
