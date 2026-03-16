package com.architectcgz.file.application.service.presigned.bridge;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.uploadsession.UploadSessionInitCoordinatorService;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresignedUploadCoreBridgeService 单元测试")
class PresignedUploadCoreBridgeServiceTest {

    @Mock
    private UploadAppService uploadAppService;
    @Mock
    private UploadSessionInitCoordinatorService uploadSessionInitCoordinatorService;
    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private BlobObjectRepository blobObjectRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private PresignedUploadStorageService presignedUploadStorageService;
    @Mock
    private AccessProperties accessProperties;

    private MultipartProperties multipartProperties;
    private PresignedUploadCoreBridgeService bridgeService;

    @BeforeEach
    void setUp() {
        multipartProperties = new MultipartProperties();
        multipartProperties.setTaskExpireHours(24);
        multipartProperties.setChunkSize(5 * 1024 * 1024);
        multipartProperties.setMaxParts(10_000);
        bridgeService = new PresignedUploadCoreBridgeService(
                uploadAppService,
                uploadSessionInitCoordinatorService,
                uploadSessionRepository,
                blobObjectRepository,
                fileAssetRepository,
                fileRecordRepository,
                fileTypeValidator,
                new PresignedUploadAccessResolver(),
                presignedUploadStorageService,
                accessProperties,
                multipartProperties
        );
    }

    @Test
    @DisplayName("申请预签名上传地址时应创建 single-upload session 并返回 PUT URL")
    void getPresignedUploadUrl_shouldCreateSingleUploadSession() {
        PresignedUploadRequest request = buildUploadRequest();
        UploadSession uploadSession = singleSession("session-ps-001", "blog/2026/03/14/user-1/uploads/avatar.png");
        when(accessProperties.getPresignedUrlExpireSeconds()).thenReturn(900);
        doNothing().when(fileTypeValidator).validateFile("avatar.png", "image/png", 512L);
        when(presignedUploadStorageService.resolveBucketName(com.architectcgz.file.domain.model.AccessLevel.PUBLIC))
                .thenReturn("public-bucket");
        when(blobObjectRepository.findByHash("blog", "hash-public", "public-bucket")).thenReturn(Optional.empty());
        when(uploadSessionInitCoordinatorService.createSession(
                anyString(),
                anyString(),
                any(UploadMode.class),
                any(AccessLevel.class),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                any(),
                anyInt(),
                anyInt()
        )).thenReturn(new UploadSessionCreationResult(uploadSession, false, false, java.util.List.of()));
        when(uploadAppService.issueSingleUploadUrl("blog", "session-ps-001", "user-1", Duration.ofSeconds(900)))
                .thenReturn(new SingleUploadUrlGrant("session-ps-001", "https://minio.example.com/presigned", 900));

        var response = bridgeService.getPresignedUploadUrl("blog", request, "user-1");

        assertThat(response.getPresignedUrl()).isEqualTo("https://minio.example.com/presigned");
        assertThat(response.getStoragePath()).isEqualTo("blog/2026/03/14/user-1/uploads/avatar.png");
        assertThat(response.getMethod()).isEqualTo("PUT");
        assertThat(response.getHeaders()).containsEntry("Content-Type", "image/png");
        verify(uploadAppService).issueSingleUploadUrl("blog", "session-ps-001", "user-1", Duration.ofSeconds(900));
    }

    @Test
    @DisplayName("用户已有同 hash 文件时应返回 FILE_ALREADY_EXISTS")
    void getPresignedUploadUrl_shouldRejectWhenUserAlreadyHasFile() {
        PresignedUploadRequest request = buildUploadRequest();
        doNothing().when(fileTypeValidator).validateFile("avatar.png", "image/png", 512L);
        when(presignedUploadStorageService.resolveBucketName(com.architectcgz.file.domain.model.AccessLevel.PUBLIC))
                .thenReturn("public-bucket");
        when(blobObjectRepository.findByHash("blog", "hash-public", "public-bucket")).thenReturn(Optional.of(mockBlob()));
        when(fileRecordRepository.findByUserIdAndFileHash("blog", "user-1", "hash-public"))
                .thenReturn(Optional.of(FileRecord.builder().id("file-001").status(com.architectcgz.file.domain.model.FileStatus.COMPLETED).build()));

        assertThatThrownBy(() -> bridgeService.getPresignedUploadUrl("blog", request, "user-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.FILE_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("确认上传时应完成 single-upload session 并返回最终文件 URL")
    void confirmUpload_shouldCompleteSessionAndReturnFileUrl() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setStoragePath("blog/2026/03/14/user-1/uploads/avatar.png");
        request.setFileHash("hash-public");
        request.setOriginalFilename("avatar.png");
        request.setAccessLevel("public");

        when(uploadSessionRepository.findActiveByHash("blog", "user-1", "hash-public"))
                .thenReturn(Optional.of(singleSession("session-ps-001", "blog/2026/03/14/user-1/uploads/avatar.png")));
        when(uploadAppService.completeSingleUpload("blog", "session-ps-001", "user-1", null))
                .thenReturn(new UploadCompletion("session-ps-001", "file-001", UploadSessionStatus.COMPLETED));
        when(fileAssetRepository.findById("file-001"))
                .thenReturn(Optional.of(new FileAsset(
                        "file-001",
                        "blog",
                        "user-1",
                        "blob-001",
                        "avatar.png",
                        "blog/2026/03/14/user-1/uploads/avatar.png",
                        "image/png",
                        512L,
                        AccessLevel.PUBLIC,
                        FileAssetStatus.ACTIVE,
                        Instant.parse("2026-03-14T00:00:00Z"),
                        Instant.parse("2026-03-14T00:00:00Z")
                )));
        when(presignedUploadStorageService.resolveBucketName(com.architectcgz.file.domain.model.AccessLevel.PUBLIC))
                .thenReturn("public-bucket");
        when(presignedUploadStorageService.resolveFileUrl(
                com.architectcgz.file.domain.model.AccessLevel.PUBLIC,
                "public-bucket",
                "blog/2026/03/14/user-1/uploads/avatar.png"
        )).thenReturn("https://cdn.example.com/avatar.png");

        Map<String, String> result = bridgeService.confirmUpload("blog", request, "user-1");

        assertThat(result).containsEntry("fileId", "file-001");
        assertThat(result).containsEntry("url", "https://cdn.example.com/avatar.png");
    }

    @Test
    @DisplayName("确认上传时找不到匹配 session 应返回 FILE_NOT_UPLOADED")
    void confirmUpload_shouldRejectWhenNoActiveSessionMatches() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setStoragePath("blog/2026/03/14/user-1/uploads/avatar.png");
        request.setFileHash("hash-public");
        request.setOriginalFilename("avatar.png");
        request.setAccessLevel("public");
        when(uploadSessionRepository.findActiveByHash("blog", "user-1", "hash-public"))
                .thenReturn(Optional.empty());
        when(uploadSessionRepository.findByOwner("blog", "user-1", 20)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> bridgeService.confirmUpload("blog", request, "user-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.FILE_NOT_UPLOADED));
    }

    private PresignedUploadRequest buildUploadRequest() {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("avatar.png");
        request.setFileSize(512L);
        request.setContentType("image/png");
        request.setFileHash("hash-public");
        request.setAccessLevel("public");
        return request;
    }

    private UploadSession singleSession(String sessionId, String objectKey) {
        return new UploadSession(
                sessionId,
                "blog",
                "user-1",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PUBLIC,
                "avatar.png",
                "image/png",
                512L,
                "hash-public",
                objectKey,
                0,
                1,
                null,
                null,
                UploadSessionStatus.INITIATED,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-15T00:00:00Z")
        );
    }

    private com.platform.fileservice.core.domain.model.BlobObject mockBlob() {
        return new com.platform.fileservice.core.domain.model.BlobObject(
                "blob-001",
                "blog",
                "legacy-s3",
                "public-bucket",
                "blog/shared/avatar.png",
                "hash-public",
                "MD5",
                512L,
                "image/png",
                1,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z")
        );
    }
}
