package com.platform.fileservice.core.web.controller;

import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.PartUploadUrl;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.domain.model.UploadedPart;
import com.platform.fileservice.core.web.config.FileCoreUploadProperties;
import com.platform.fileservice.core.web.exception.V1GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1UploadSessionController.class)
@Import({V1GlobalExceptionHandler.class, V1UploadSessionControllerTest.TestPropsConfiguration.class})
@TestPropertySource(properties = {
        "file.core.upload.session-ttl=24h",
        "file.core.upload.part-url-ttl=15m",
        "file.core.upload.chunk-size-bytes=5242880",
        "file.core.upload.max-parts=10000",
        "file.core.upload.auto-presigned-single-max-size-bytes=10485760"
})
class V1UploadSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadAppService uploadAppService;

    @Test
    void shouldCreateUploadSession() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.DIRECT),
                eq(AccessLevel.PRIVATE),
                eq("demo.mp4"),
                eq("video/mp4"),
                eq(1024L),
                eq("hash-001"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                uploadSession("session-001", UploadSessionStatus.UPLOADING),
                false,
                false,
                List.of()
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "DIRECT",
                                  "accessLevel": "PRIVATE",
                                  "originalFilename": "demo.mp4",
                                  "contentType": "video/mp4",
                                  "expectedSize": 1024,
                                  "fileHash": "hash-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadSessionId").value("session-001"))
                .andExpect(jsonPath("$.uploadSession.uploadMode").value("DIRECT"))
                .andExpect(jsonPath("$.uploadSession.accessLevel").value("PRIVATE"))
                .andExpect(jsonPath("$.uploadSession.status").value("UPLOADING"))
                .andExpect(jsonPath("$.uploadSession.chunkSizeBytes").value(5 * 1024 * 1024))
                .andExpect(jsonPath("$.uploadSession.totalParts").value(1))
                .andExpect(jsonPath("$.uploadSession.fileId").isEmpty())
                .andExpect(jsonPath("$.resumed").value(false))
                .andExpect(jsonPath("$.instantUpload").value(false))
                .andExpect(jsonPath("$.completedPartNumbers").isArray())
                .andExpect(jsonPath("$.singleUploadUrl").isEmpty())
                .andExpect(jsonPath("$.singleUploadMethod").isEmpty())
                .andExpect(jsonPath("$.singleUploadExpiresInSeconds").isEmpty());
    }

    @Test
    void shouldReturnCompletedSessionWhenInstantUploadHitsExistingBlob() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.DIRECT),
                eq(AccessLevel.PRIVATE),
                eq("demo.mp4"),
                eq("video/mp4"),
                eq(1024L),
                eq("hash-001"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                instantUploadSession("session-002", "file-001"),
                false,
                true,
                List.of()
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "DIRECT",
                                  "accessLevel": "PRIVATE",
                                  "originalFilename": "demo.mp4",
                                  "contentType": "video/mp4",
                                  "expectedSize": 1024,
                                  "fileHash": "hash-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadSessionId").value("session-002"))
                .andExpect(jsonPath("$.uploadSession.status").value("COMPLETED"))
                .andExpect(jsonPath("$.uploadSession.fileId").value("file-001"))
                .andExpect(jsonPath("$.uploadSession.totalParts").value(0))
                .andExpect(jsonPath("$.instantUpload").value(true));
    }

    @Test
    void shouldReturnCompletedPartsWhenCreateResumesExistingSession() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.DIRECT),
                eq(AccessLevel.PRIVATE),
                eq("demo.mp4"),
                eq("video/mp4"),
                eq(1024L),
                eq("hash-001"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                uploadSession("session-003", UploadSessionStatus.UPLOADING),
                true,
                false,
                List.of(
                        new UploadedPart(1, "etag-1", 1024L),
                        new UploadedPart(2, "etag-2", 1024L)
                )
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "DIRECT",
                                  "accessLevel": "PRIVATE",
                                  "originalFilename": "demo.mp4",
                                  "contentType": "video/mp4",
                                  "expectedSize": 1024,
                                  "fileHash": "hash-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadSessionId").value("session-003"))
                .andExpect(jsonPath("$.resumed").value(true))
                .andExpect(jsonPath("$.completedPartNumbers[0]").value(1))
                .andExpect(jsonPath("$.completedPartInfos[1].etag").value("etag-2"));
    }

    @Test
    void shouldReturnSingleUploadUrlWhenCreatePresignedSingleSession() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.PRESIGNED_SINGLE),
                eq(AccessLevel.PUBLIC),
                eq("avatar.png"),
                eq("image/png"),
                eq(512L),
                eq("hash-ps-001"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                presignedSingleSession("session-ps-001", UploadSessionStatus.INITIATED),
                false,
                false,
                List.of()
        ));
        when(uploadAppService.issueSingleUploadUrl(
                "blog",
                "session-ps-001",
                "user-001",
                Duration.ofMinutes(15)
        )).thenReturn(new SingleUploadUrlGrant(
                "session-ps-001",
                "https://upload.example.com/object",
                900
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "PRESIGNED_SINGLE",
                                  "accessLevel": "PUBLIC",
                                  "originalFilename": "avatar.png",
                                  "contentType": "image/png",
                                  "expectedSize": 512,
                                  "fileHash": "hash-ps-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadSessionId").value("session-ps-001"))
                .andExpect(jsonPath("$.uploadSession.uploadMode").value("PRESIGNED_SINGLE"))
                .andExpect(jsonPath("$.singleUploadUrl").value("https://upload.example.com/object"))
                .andExpect(jsonPath("$.singleUploadMethod").value("PUT"))
                .andExpect(jsonPath("$.singleUploadExpiresInSeconds").value(900))
                .andExpect(jsonPath("$.singleUploadHeaders.Content-Type").value("image/png"));
    }

    @Test
    void shouldResolveAutoModeToPresignedSingleForSmallFiles() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.PRESIGNED_SINGLE),
                eq(AccessLevel.PUBLIC),
                eq("avatar.png"),
                eq("image/png"),
                eq(512L),
                eq("hash-auto-small"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                presignedSingleSession("session-auto-001", UploadSessionStatus.INITIATED),
                false,
                false,
                List.of()
        ));
        when(uploadAppService.issueSingleUploadUrl(
                "blog",
                "session-auto-001",
                "user-001",
                Duration.ofMinutes(15)
        )).thenReturn(new SingleUploadUrlGrant(
                "session-auto-001",
                "https://upload.example.com/auto-object",
                900
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "AUTO",
                                  "accessLevel": "PUBLIC",
                                  "originalFilename": "avatar.png",
                                  "contentType": "image/png",
                                  "expectedSize": 512,
                                  "fileHash": "hash-auto-small"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadMode").value("PRESIGNED_SINGLE"))
                .andExpect(jsonPath("$.singleUploadUrl").value("https://upload.example.com/auto-object"));
    }

    @Test
    void shouldResolveAutoModeToDirectForLargeFiles() throws Exception {
        when(uploadAppService.createSession(
                eq("blog"),
                eq("user-001"),
                eq(UploadMode.DIRECT),
                eq(AccessLevel.PRIVATE),
                eq("movie.mp4"),
                eq("video/mp4"),
                eq(20L * 1024 * 1024),
                eq("hash-auto-large"),
                eq(Duration.ofHours(24)),
                eq(5 * 1024 * 1024),
                eq(10_000)
        )).thenReturn(new UploadSessionCreationResult(
                uploadSession("session-auto-002", UploadSessionStatus.UPLOADING),
                false,
                false,
                List.of()
        ));

        mockMvc.perform(post("/api/v1/upload-sessions")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "uploadMode": "AUTO",
                                  "accessLevel": "PRIVATE",
                                  "originalFilename": "movie.mp4",
                                  "contentType": "video/mp4",
                                  "expectedSize": 20971520,
                                  "fileHash": "hash-auto-large"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSession.uploadMode").value("DIRECT"))
                .andExpect(jsonPath("$.singleUploadUrl").isEmpty());
    }

    @Test
    void shouldReturnUploadSession() throws Exception {
        when(uploadAppService.getVisibleSession("blog", "session-001", "user-001"))
                .thenReturn(uploadSession("session-001", UploadSessionStatus.UPLOADING));

        mockMvc.perform(get("/api/v1/upload-sessions/{uploadSessionId}", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSessionId").value("session-001"))
                .andExpect(jsonPath("$.status").value("UPLOADING"))
                .andExpect(jsonPath("$.chunkSizeBytes").value(5 * 1024 * 1024))
                .andExpect(jsonPath("$.totalParts").value(1))
                .andExpect(jsonPath("$.fileId").isEmpty());

        verify(uploadAppService).getVisibleSession("blog", "session-001", "user-001");
    }

    @Test
    void shouldCreatePartUploadUrls() throws Exception {
        when(uploadAppService.issuePartUploadUrls(
                "blog",
                "session-001",
                "user-001",
                List.of(1, 2),
                Duration.ofMinutes(15)
        )).thenReturn(new PartUploadUrlGrant(
                "session-001",
                List.of(
                        new PartUploadUrl(1, "https://upload.example.com/part/1", 900),
                        new PartUploadUrl(2, "https://upload.example.com/part/2", 900)
                )
        ));

        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/part-urls", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "partNumbers": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSessionId").value("session-001"))
                .andExpect(jsonPath("$.partUrls[0].partNumber").value(1))
                .andExpect(jsonPath("$.partUrls[0].uploadUrl").value("https://upload.example.com/part/1"))
                .andExpect(jsonPath("$.partUrls[0].expiresInSeconds").value(900))
                .andExpect(jsonPath("$.partUrls[1].partNumber").value(2));
    }

    @Test
    void shouldReturnUploadProgress() throws Exception {
        when(uploadAppService.getUploadProgress("blog", "session-001", "user-001"))
                .thenReturn(new UploadProgress(
                        "session-001",
                        3,
                        2,
                        10L * 1024 * 1024,
                        11L * 1024 * 1024,
                        90,
                        List.of(
                                new UploadedPart(1, "etag-1", 5L * 1024 * 1024),
                                new UploadedPart(2, "etag-2", 5L * 1024 * 1024)
                        )
                ));

        mockMvc.perform(get("/api/v1/upload-sessions/{uploadSessionId}/progress", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSessionId").value("session-001"))
                .andExpect(jsonPath("$.completedParts").value(2))
                .andExpect(jsonPath("$.uploadedBytes").value(10L * 1024 * 1024))
                .andExpect(jsonPath("$.completedPartNumbers[0]").value(1))
                .andExpect(jsonPath("$.completedPartInfos[1].etag").value("etag-2"));
    }

    @Test
    void shouldAbortUploadSession() throws Exception {
        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/abort", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isNoContent());

        verify(uploadAppService).abortSession("blog", "session-001", "user-001");
    }

    @Test
    void shouldCompleteUploadSession() throws Exception {
        when(uploadAppService.getVisibleSession("blog", "session-001", "user-001"))
                .thenReturn(uploadSession("session-001", UploadSessionStatus.UPLOADING));
        when(uploadAppService.completeSession(
                "blog",
                "session-001",
                "user-001",
                "video/mp4",
                List.of()
        )).thenReturn(new UploadCompletion("session-001", "file-001", UploadSessionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/complete", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentType": "video/mp4",
                                  "parts": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSessionId").value("session-001"))
                .andExpect(jsonPath("$.fileId").value("file-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldCompletePresignedSingleUploadSession() throws Exception {
        when(uploadAppService.getVisibleSession("blog", "session-ps-001", "user-001"))
                .thenReturn(presignedSingleSession("session-ps-001", UploadSessionStatus.INITIATED));
        when(uploadAppService.completeSingleUpload(
                "blog",
                "session-ps-001",
                "user-001",
                "image/png"
        )).thenReturn(new UploadCompletion("session-ps-001", "file-ps-001", UploadSessionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/complete", "session-ps-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentType": "image/png",
                                  "parts": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadSessionId").value("session-ps-001"))
                .andExpect(jsonPath("$.fileId").value("file-ps-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnBadRequestWhenUploadSessionInvalid() throws Exception {
        doThrow(new UploadSessionInvalidRequestException("invalid"))
                .when(uploadAppService)
                .issuePartUploadUrls("blog", "session-001", "user-001", List.of(1), Duration.ofMinutes(15));

        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/part-urls", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "partNumbers": [1]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid"));
    }

    @Test
    void shouldReturnBadRequestWhenCompleteRequestInvalid() throws Exception {
        when(uploadAppService.getVisibleSession("blog", "session-001", "user-001"))
                .thenReturn(uploadSession("session-001", UploadSessionStatus.UPLOADING));
        doThrow(new UploadSessionInvalidRequestException("invalid"))
                .when(uploadAppService)
                .completeSession("blog", "session-001", "user-001", "video/mp4", List.of());

        mockMvc.perform(post("/api/v1/upload-sessions/{uploadSessionId}/complete", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentType": "video/mp4",
                                  "parts": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid"));
    }

    @Test
    void shouldReturnForbiddenWhenUploadSessionDenied() throws Exception {
        doThrow(new UploadSessionAccessDeniedException("denied"))
                .when(uploadAppService)
                .getVisibleSession("blog", "session-001", "user-002");

        mockMvc.perform(get("/api/v1/upload-sessions/{uploadSessionId}", "session-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-002"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("denied"));
    }

    @Test
    void shouldReturnNotFoundWhenUploadSessionMissing() throws Exception {
        doThrow(new UploadSessionNotFoundException("missing"))
                .when(uploadAppService)
                .getVisibleSession("blog", "session-404", "user-001");

        mockMvc.perform(get("/api/v1/upload-sessions/{uploadSessionId}", "session-404")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"));
    }

    private UploadSession uploadSession(String uploadSessionId, UploadSessionStatus status) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                1024L,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                5 * 1024 * 1024,
                1,
                "provider-001",
                null,
                status,
                now,
                now,
                now.plus(Duration.ofHours(24))
        );
    }

    private UploadSession instantUploadSession(String uploadSessionId, String fileId) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                1024L,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/existing-demo.mp4",
                0,
                0,
                null,
                fileId,
                UploadSessionStatus.COMPLETED,
                now,
                now,
                now.plus(Duration.ofHours(24))
        );
    }

    private UploadSession presignedSingleSession(String uploadSessionId, UploadSessionStatus status) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PUBLIC,
                "avatar.png",
                "image/png",
                512L,
                "hash-ps-001",
                "blog/2026/03/14/user-001/uploads/avatar.png",
                0,
                1,
                null,
                null,
                status,
                now,
                now,
                now.plus(Duration.ofHours(24))
        );
    }

    @TestConfiguration
    @EnableConfigurationProperties(FileCoreUploadProperties.class)
    static class TestPropsConfiguration {
    }
}
