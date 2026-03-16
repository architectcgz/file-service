package com.architectcgz.file.application.service.direct.bridge;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.service.uploadsession.UploadSessionInitCoordinatorService;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.PartUploadUrl;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.domain.model.UploadedPart;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadCoreBridgeService 单元测试")
class DirectUploadCoreBridgeServiceTest {

    @Mock
    private UploadAppService uploadAppService;
    @Mock
    private UploadSessionInitCoordinatorService uploadSessionInitCoordinatorService;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private AccessProperties accessProperties;

    private MultipartProperties multipartProperties;
    private DirectUploadCoreBridgeService bridgeService;

    @BeforeEach
    void setUp() {
        multipartProperties = new MultipartProperties();
        multipartProperties.setChunkSize(5 * 1024 * 1024);
        multipartProperties.setMaxParts(10_000);
        multipartProperties.setTaskExpireHours(24);
        bridgeService = new DirectUploadCoreBridgeService(
                uploadAppService,
                uploadSessionInitCoordinatorService,
                objectStoragePort,
                accessProperties,
                multipartProperties
        );
    }

    @Test
    @DisplayName("新建直传任务时应映射为 legacy init 响应")
    void initDirectUpload_shouldMapFreshSessionToLegacyResponse() {
        DirectUploadInitRequest request = buildRequest();
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
                buildUploadingSession("task-001", "upload-001", "blog/2026/03/14/user-123/files/report.pdf"),
                false,
                false,
                List.of()
        ));

        var response = bridgeService.initDirectUpload("blog", request, "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getUploadId()).isEqualTo("upload-001");
        assertThat(response.getStoragePath()).isEqualTo("blog/2026/03/14/user-123/files/report.pdf");
        assertThat(response.getChunkSize()).isEqualTo(5 * 1024 * 1024);
        assertThat(response.getTotalParts()).isEqualTo(4);
        assertThat(response.getCompletedParts()).isEmpty();
        assertThat(response.getCompletedPartInfos()).isEmpty();
        assertThat(response.getIsResume()).isFalse();
        assertThat(response.getIsInstantUpload()).isFalse();
    }

    @Test
    @DisplayName("断点续传时应携带已上传分片信息")
    void initDirectUpload_shouldMapResumedSessionToLegacyResponse() {
        DirectUploadInitRequest request = buildRequest();
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
                buildUploadingSession("task-002", "upload-002", "blog/2026/03/14/user-123/files/report.pdf"),
                true,
                false,
                List.of(
                        new UploadedPart(1, "\"etag-1\"", 5L),
                        new UploadedPart(2, "\"etag-2\"", 5L)
                )
        ));

        var response = bridgeService.initDirectUpload("blog", request, "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-002");
        assertThat(response.getCompletedParts()).containsExactly(1, 2);
        assertThat(response.getCompletedPartInfos())
                .extracting(partInfo -> partInfo.getPartNumber() + ":" + partInfo.getEtag())
                .containsExactly("1:\"etag-1\"", "2:\"etag-2\"");
        assertThat(response.getIsResume()).isTrue();
        assertThat(response.getIsInstantUpload()).isFalse();
    }

    @Test
    @DisplayName("秒传命中时应返回文件ID和公开访问地址")
    void initDirectUpload_shouldMapInstantUploadToLegacyResponse() {
        DirectUploadInitRequest request = buildRequest();
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
                new UploadSession(
                        "task-003",
                        "blog",
                        "user-123",
                        UploadMode.DIRECT,
                        AccessLevel.PUBLIC,
                        "report.pdf",
                        "application/pdf",
                        20L,
                        "hash-123",
                        "blog/2026/03/14/user-123/files/report.pdf",
                        0,
                        0,
                        null,
                        "file-001",
                        UploadSessionStatus.COMPLETED,
                        Instant.parse("2026-03-14T00:00:00Z"),
                        Instant.parse("2026-03-14T00:00:00Z"),
                        Instant.parse("2026-03-15T00:00:00Z")
                ),
                false,
                true,
                List.of()
        ));
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(objectStoragePort.resolveObjectUri("public-bucket", "blog/2026/03/14/user-123/files/report.pdf"))
                .thenReturn(URI.create("https://cdn.example.com/blog/2026/03/14/user-123/files/report.pdf"));

        var response = bridgeService.initDirectUpload("blog", request, "user-123");

        assertThat(response.getIsInstantUpload()).isTrue();
        assertThat(response.getIsResume()).isFalse();
        assertThat(response.getFileId()).isEqualTo("file-001");
        assertThat(response.getFileUrl()).isEqualTo("https://cdn.example.com/blog/2026/03/14/user-123/files/report.pdf");
        assertThat(response.getTaskId()).isNull();
        verify(objectStoragePort).resolveBucketName(AccessLevel.PUBLIC);
    }

    @Test
    @DisplayName("core 返回无效请求时应转换为 legacy BusinessException")
    void initDirectUpload_shouldTranslateInvalidRequest() {
        DirectUploadInitRequest request = buildRequest();
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
        )).thenThrow(new UploadSessionInvalidRequestException("existing upload session size mismatch for fileHash"));

        assertThatThrownBy(() -> bridgeService.initDirectUpload("blog", request, "user-123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.FILE_SIZE_MISMATCH));
    }

    @Test
    @DisplayName("获取分片上传地址时应映射 core grant")
    void getPartUploadUrls_shouldMapCoreGrant() {
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1, 2));
        when(accessProperties.getPresignedUrlExpireSeconds()).thenReturn(900);
        when(uploadAppService.issuePartUploadUrls("blog", "task-001", "user-123", List.of(1, 2), java.time.Duration.ofSeconds(900)))
                .thenReturn(new PartUploadUrlGrant(
                        "task-001",
                        List.of(
                                new PartUploadUrl(1, "https://upload/1", 900),
                                new PartUploadUrl(2, "https://upload/2", 900)
                        )
                ));

        var response = bridgeService.getPartUploadUrls("blog", request, "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getPartUrls()).hasSize(2);
        assertThat(response.getPartUrls().get(0).getUploadUrl()).isEqualTo("https://upload/1");
    }

    @Test
    @DisplayName("获取分片上传地址时 access denied 应转成 legacy 异常")
    void getPartUploadUrls_shouldTranslateAccessDenied() {
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1));
        when(accessProperties.getPresignedUrlExpireSeconds()).thenReturn(900);
        when(uploadAppService.issuePartUploadUrls("blog", "task-001", "user-123", List.of(1), java.time.Duration.ofSeconds(900)))
                .thenThrow(new UploadSessionAccessDeniedException("denied"));

        assertThatThrownBy(() -> bridgeService.getPartUploadUrls("blog", request, "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("查询上传进度时应映射 core progress")
    void getUploadProgress_shouldMapCoreProgress() {
        when(uploadAppService.getUploadProgress("blog", "task-001", "user-123"))
                .thenReturn(new UploadProgress(
                        "task-001",
                        4,
                        2,
                        10L,
                        20L,
                        50,
                        List.of(
                                new UploadedPart(1, "etag-1", 5L),
                                new UploadedPart(3, "etag-3", 5L)
                        )
                ));

        var response = bridgeService.getUploadProgress("blog", "task-001", "user-123");

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getCompletedParts()).isEqualTo(2);
        assertThat(response.getCompletedPartNumbers()).containsExactly(1, 3);
        assertThat(response.getPercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("完成上传时应返回 core 生成的 fileId")
    void completeDirectUpload_shouldReturnCoreFileId() {
        DirectUploadCompleteRequest request = buildCompleteRequest();
        when(uploadAppService.completeSession(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(new UploadCompletion("task-001", "file-001", UploadSessionStatus.COMPLETED));

        String fileId = bridgeService.completeDirectUpload("blog", request, "user-123");

        assertThat(fileId).isEqualTo("file-001");
    }

    @Test
    @DisplayName("完成上传时应把 core 的 etag mismatch 转成 legacy 错误码和消息")
    void completeDirectUpload_shouldTranslatePartEtagMismatch() {
        DirectUploadCompleteRequest request = buildCompleteRequest();
        when(uploadAppService.completeSession(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenThrow(new UploadSessionInvalidRequestException("part etag mismatch: 1"));

        assertThatThrownBy(() -> bridgeService.completeDirectUpload("blog", request, "user-123"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.PART_ETAG_MISMATCH);
                    assertThat(ex.getMessage()).isEqualTo("分片ETag不匹配: 1");
                });
    }

    @Test
    @DisplayName("完成上传时缺少 fileHash 应转成 legacy 稳定错误码")
    void completeDirectUpload_shouldTranslateMissingFileHash() {
        DirectUploadCompleteRequest request = buildCompleteRequest();
        when(uploadAppService.completeSession(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenThrow(new UploadSessionInvalidRequestException("upload session fileHash is required to complete upload"));

        assertThatThrownBy(() -> bridgeService.completeDirectUpload("blog", request, "user-123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(FileServiceErrorCodes.UPLOAD_TASK_FILE_HASH_MISSING));
    }

    private DirectUploadInitRequest buildRequest() {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        request.setFileName("report.pdf");
        request.setFileSize(20L);
        request.setContentType("application/pdf");
        request.setFileHash("hash-123");
        return request;
    }

    private DirectUploadCompleteRequest buildCompleteRequest() {
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        DirectUploadCompleteRequest.PartInfo partInfo = new DirectUploadCompleteRequest.PartInfo();
        partInfo.setPartNumber(1);
        partInfo.setEtag("etag-1");
        request.setParts(List.of(partInfo));
        return request;
    }

    private UploadSession buildUploadingSession(String taskId, String uploadId, String storagePath) {
        return new UploadSession(
                taskId,
                "blog",
                "user-123",
                UploadMode.DIRECT,
                AccessLevel.PUBLIC,
                "report.pdf",
                "application/pdf",
                20L,
                "hash-123",
                storagePath,
                5 * 1024 * 1024,
                4,
                uploadId,
                null,
                UploadSessionStatus.UPLOADING,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-15T00:00:00Z")
        );
    }
}
