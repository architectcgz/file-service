package com.architectcgz.file.application.service.presigned.command;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.service.presigned.factory.PresignedUploadObjectFactory;
import com.architectcgz.file.application.service.presigned.query.PresignedStorageObjectQueryService;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.ObjectMetadata;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresignedUploadConfirmCommandService 单元测试")
class PresignedUploadConfirmCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;

    private PresignedUploadConfirmCommandService presignedUploadConfirmCommandService;

    @BeforeEach
    void setUp() {
        PresignedUploadStorageService presignedUploadStorageService = new PresignedUploadStorageService(
                s3StorageService,
                s3StorageService
        );
        ReflectionTestUtils.setField(presignedUploadStorageService, "presignedUrlExpireSeconds", 300);
        presignedUploadConfirmCommandService = new PresignedUploadConfirmCommandService(
                new PresignedUploadAccessResolver(),
                new PresignedStorageObjectQueryService(storageObjectRepository),
                new PresignedUploadObjectFactory(),
                presignedUploadStorageService,
                storageObjectRepository,
                fileRecordRepository
        );
    }

    @Test
    @DisplayName("确认私有上传时应写入 private bucket 并返回预签名访问地址")
    void confirmUpload_shouldPersistPrivateFileRecordAndPresignedUrl() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setAppId("blog");
        request.setStoragePath("blog/private/object.png");
        request.setFileHash("hash-private");
        request.setOriginalFilename("object.png");
        request.setAccessLevel("private");

        StorageObject existingStorageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .fileHash("hash-private")
                .storagePath("blog/private/existing.png")
                .bucketName("private-bucket")
                .fileSize(128L)
                .contentType("image/png")
                .referenceCount(1)
                .build();

        doReturn("private-bucket").when(s3StorageService).getBucketName(AccessLevel.PRIVATE);
        when(s3StorageService.getObjectMetadata("private-bucket", "blog/private/object.png"))
                .thenReturn(ObjectMetadata.builder().fileSize(256L).contentType("image/png").build());
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-private", "private-bucket"))
                .thenReturn(Optional.of(existingStorageObject));
        when(s3StorageService.generatePresignedUrl("private-bucket", "blog/private/existing.png", Duration.ofSeconds(300)))
                .thenReturn("https://minio.example.com/private-signed");
        when(fileRecordRepository.save(any(FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<FileRecord> fileRecordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        Map<String, String> result = presignedUploadConfirmCommandService.confirmUpload("blog", request, "user-1");

        assertThat(result.get("url")).isEqualTo("https://minio.example.com/private-signed");
        verify(storageObjectRepository).incrementReferenceCount("storage-001");
        verify(fileRecordRepository).save(fileRecordCaptor.capture());
        assertThat(fileRecordCaptor.getValue().getAccessLevel()).isEqualTo(AccessLevel.PRIVATE);
        assertThat(fileRecordCaptor.getValue().getStoragePath()).isEqualTo("blog/private/existing.png");
        assertThat(fileRecordCaptor.getValue().getStorageObjectId()).isEqualTo("storage-001");
    }
}
