package com.architectcgz.file.application.service.multipart.bridge;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.uploadsession.UploadSessionInitCoordinatorService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.domain.model.UploadedPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadCoreBridgeService 单元测试")
class MultipartUploadCoreBridgeServiceTest {

    @Mock
    private UploadAppService uploadAppService;
    @Mock
    private UploadSessionInitCoordinatorService uploadSessionInitCoordinatorService;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;

    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    @BeforeEach
    void setUp() {
        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setTaskExpireHours(24);
        multipartProperties.setChunkSize(1024);
        multipartProperties.setMaxParts(10);
        multipartUploadCoreBridgeService = new MultipartUploadCoreBridgeService(
                uploadAppService,
                uploadSessionInitCoordinatorService,
                fileTypeValidator,
                tenantDomainService,
                multipartProperties
        );
    }

    @Test
    @DisplayName("初始化分片上传时应完成 legacy 校验并映射新建任务")
    void initUpload_shouldValidateAndMapFreshSession() {
        InitUploadRequest request = buildInitRequest();
        when(tenantDomainService.validateUploadPrerequisites("blog", 2048L)).thenReturn(null);
        doNothing().when(fileTypeValidator).validateFile("archive.zip", "application/zip", 2048L);
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
        )).thenReturn(new UploadSessionCreationResult(
                buildSession("task-001", "upload-001", UploadSessionStatus.UPLOADING, Instant.parse("2026-03-15T00:00:00Z")),
                false,
                false,
                List.of()
        ));

        var response = multipartUploadCoreBridgeService.initUpload("blog", request, "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getUploadId()).isEqualTo("upload-001");
        assertThat(response.getChunkSize()).isEqualTo(1024);
        assertThat(response.getTotalParts()).isEqualTo(2);
        assertThat(response.getCompletedParts()).isEmpty();
        verify(tenantDomainService).validateUploadPrerequisites("blog", 2048L);
        verify(fileTypeValidator).validateFile("archive.zip", "application/zip", 2048L);
        verify(uploadSessionInitCoordinatorService).createSession(
                "blog",
                "user-123",
                UploadMode.DIRECT,
                AccessLevel.PUBLIC,
                "archive.zip",
                "application/zip",
                2048L,
                "hash-456",
                Duration.ofHours(24),
                1024,
                10
        );
    }

    @Test
    @DisplayName("初始化分片上传续传时应返回已完成分片")
    void initUpload_shouldMapResumedSession() {
        InitUploadRequest request = buildInitRequest();
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
        )).thenReturn(new UploadSessionCreationResult(
                buildSession("task-002", "upload-002", UploadSessionStatus.UPLOADING, Instant.parse("2026-03-15T00:00:00Z")),
                true,
                false,
                List.of(new UploadedPart(1, "etag-1", 1024L), new UploadedPart(2, "etag-2", 1024L))
        ));

        var response = multipartUploadCoreBridgeService.initUpload("blog", request, "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-002");
        assertThat(response.getCompletedParts()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("上传分片时应直接委托 core service")
    void uploadPart_shouldDelegateToCoreService() {
        byte[] data = "data".getBytes();
        when(uploadAppService.uploadPart("blog", "task-001", "user-123", 1, data)).thenReturn("etag-1");

        String actual = multipartUploadCoreBridgeService.uploadPart("blog", "task-001", 1, data, "user-123");

        assertThat(actual).isEqualTo("etag-1");
    }

    @Test
    @DisplayName("上传分片时非法分片号应转换为稳定错误码")
    void uploadPart_shouldTranslatePartNumberInvalid() {
        when(uploadAppService.uploadPart("blog", "task-001", "user-123", 3, "data".getBytes()))
                .thenThrow(new UploadSessionInvalidRequestException("partNumber out of range: 3"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.uploadPart("blog", "task-001", 3, "data".getBytes(), "user-123"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.PART_NUMBER_INVALID);
                    assertThat(ex.getMessage()).isEqualTo(String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, 3));
                });
    }

    @Test
    @DisplayName("上传分片时无权限应转换为 legacy 拒绝异常")
    void uploadPart_shouldTranslateAccessDenied() {
        when(uploadAppService.uploadPart("blog", "task-001", "user-123", 1, "data".getBytes()))
                .thenThrow(new UploadSessionAccessDeniedException("denied"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.uploadPart("blog", "task-001", 1, "data".getBytes(), "user-123"))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK));
    }

    @Test
    @DisplayName("完成分片上传时应返回 core 生成的 fileId")
    void completeUpload_shouldReturnFileId() {
        when(uploadAppService.completeSession("blog", "task-001", "user-123", null, List.of()))
                .thenReturn(new UploadCompletion("task-001", "file-001", UploadSessionStatus.COMPLETED));

        String actual = multipartUploadCoreBridgeService.completeUpload("blog", "task-001", "user-123");

        assertThat(actual).isEqualTo("file-001");
    }

    @Test
    @DisplayName("完成分片上传时缺少 fileHash 应转换为稳定错误码")
    void completeUpload_shouldTranslateMissingFileHash() {
        when(uploadAppService.completeSession("blog", "task-001", "user-123", null, List.of()))
                .thenThrow(new UploadSessionInvalidRequestException("upload session fileHash is required to complete upload"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.completeUpload("blog", "task-001", "user-123"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.UPLOAD_TASK_FILE_HASH_MISSING);
                    assertThat(ex.getMessage()).isEqualTo(FileServiceErrorMessages.UPLOAD_TASK_FILE_HASH_MISSING);
                });
    }

    @Test
    @DisplayName("完成分片上传时分片不完整应转换为稳定错误码")
    void completeUpload_shouldTranslatePartsIncomplete() {
        when(uploadAppService.completeSession("blog", "task-001", "user-123", null, List.of()))
                .thenThrow(new UploadSessionInvalidRequestException("uploaded parts incomplete: 1/2"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.completeUpload("blog", "task-001", "user-123"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.PARTS_INCOMPLETE);
                    assertThat(ex.getMessage()).isEqualTo(String.format(FileServiceErrorMessages.PARTS_INCOMPLETE, 1, 2));
                });
    }

    @Test
    @DisplayName("完成分片上传时存储变更失败应转换为 legacy 上传失败")
    void completeUpload_shouldTranslateMutationFailure() {
        when(uploadAppService.completeSession("blog", "task-001", "user-123", null, List.of()))
                .thenThrow(new UploadSessionMutationException("s3 failed"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.completeUpload("blog", "task-001", "user-123"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.FILE_UPLOAD_FAILED);
                    assertThat(ex.getMessage()).contains("s3 failed");
                });
    }

    @Test
    @DisplayName("中止分片上传时任务不存在应转换为稳定错误码")
    void abortUpload_shouldTranslateNotFound() {
        doThrow(new UploadSessionNotFoundException("task-001"))
                .when(uploadAppService)
                .abortSession("blog", "task-001", "user-123");

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.abortUpload("blog", "task-001", "user-123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("查询上传进度时应映射 core 进度")
    void getProgress_shouldMapCoreProgress() {
        when(uploadAppService.getUploadProgress("blog", "task-001", "user-123"))
                .thenReturn(new UploadProgress("task-001", 2, 1, 1024L, 2048L, 50, List.of()));

        UploadProgressResponse response = multipartUploadCoreBridgeService.getProgress("blog", "task-001", "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getTotalParts()).isEqualTo(2);
        assertThat(response.getCompletedParts()).isEqualTo(1);
        assertThat(response.getUploadedBytes()).isEqualTo(1024L);
        assertThat(response.getTotalBytes()).isEqualTo(2048L);
        assertThat(response.getPercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("查询上传进度时查看权限不足应转换为 legacy 拒绝异常")
    void getProgress_shouldTranslateViewAccessDenied() {
        when(uploadAppService.getUploadProgress("blog", "task-001", "user-123"))
                .thenThrow(new UploadSessionAccessDeniedException("denied"));

        assertThatThrownBy(() -> multipartUploadCoreBridgeService.getProgress("blog", "task-001", "user-123"))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK));
    }

    @Test
    @DisplayName("查询上传任务列表时应只返回 direct 会话并映射 legacy 状态")
    void listTasks_shouldFilterAndMapLegacyTasks() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        when(uploadAppService.listVisibleSessions("blog", "user-123", 100))
                .thenReturn(List.of(
                        buildSession("task-001", "upload-001", UploadSessionStatus.UPLOADING, now.plus(Duration.ofHours(1))),
                        buildSession("task-002", "upload-002", UploadSessionStatus.COMPLETED, now.plus(Duration.ofHours(1))),
                        buildSession("task-003", "upload-003", UploadSessionStatus.EXPIRED, now.minus(Duration.ofHours(1))),
                        new UploadSession(
                                "task-004",
                                "blog",
                                "user-123",
                                UploadMode.PRESIGNED_SINGLE,
                                AccessLevel.PUBLIC,
                                "avatar.png",
                                "image/png",
                                512L,
                                "hash-789",
                                "blog/2026/03/14/user-123/files/avatar.png",
                                0,
                                1,
                                null,
                                null,
                                UploadSessionStatus.INITIATED,
                                now,
                                now,
                                now.plus(Duration.ofHours(1))
                        )
                ));

        List<UploadTask> tasks = multipartUploadCoreBridgeService.listTasks("blog", "user-123");

        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(UploadTask::getId).containsExactly("task-001", "task-002", "task-003");
        assertThat(tasks).extracting(UploadTask::getStatus)
                .containsExactly(UploadTaskStatus.UPLOADING, UploadTaskStatus.COMPLETED, UploadTaskStatus.EXPIRED);
        assertThat(tasks.get(0).getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
        assertThat(tasks.get(0).getStoragePath()).isEqualTo("blog/2026/03/14/user-123/files/archive.zip");
    }

    private InitUploadRequest buildInitRequest() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("archive.zip");
        request.setFileSize(2048L);
        request.setContentType("application/zip");
        request.setFileHash("hash-456");
        return request;
    }

    private UploadSession buildSession(String sessionId, String uploadId, UploadSessionStatus status, Instant expiresAt) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        return new UploadSession(
                sessionId,
                "blog",
                "user-123",
                UploadMode.DIRECT,
                AccessLevel.PUBLIC,
                "archive.zip",
                "application/zip",
                2048L,
                "hash-456",
                "blog/2026/03/14/user-123/files/archive.zip",
                1024,
                2,
                uploadId,
                status == UploadSessionStatus.COMPLETED ? "file-001" : null,
                status,
                now,
                now,
                expiresAt
        );
    }
}
