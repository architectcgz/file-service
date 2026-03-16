package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class CleanupAppServiceTest {

    private UploadSessionRepository uploadSessionRepository;
    private ObjectStoragePort objectStoragePort;
    private CleanupAppService cleanupAppService;

    @BeforeEach
    void setUp() {
        uploadSessionRepository = mock(UploadSessionRepository.class);
        objectStoragePort = mock(ObjectStoragePort.class);
        ClockPort clockPort = () -> Instant.parse("2026-03-14T03:00:00Z");
        cleanupAppService = new CleanupAppService(uploadSessionRepository, objectStoragePort, clockPort);
    }

    @Test
    void shouldAbortMultipartContextBeforeMarkingExpired() {
        when(uploadSessionRepository.findExpiredSessions(Instant.parse("2026-03-14T03:00:00Z")))
                .thenReturn(List.of(session("session-001"), presignedSession("session-ps-001"), inlineSession("session-002")));
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadSessionRepository.updateStatus("session-001", UploadSessionStatus.EXPIRED)).thenReturn(true);
        when(uploadSessionRepository.updateStatus("session-ps-001", UploadSessionStatus.EXPIRED)).thenReturn(true);
        when(uploadSessionRepository.updateStatus("session-002", UploadSessionStatus.EXPIRED)).thenReturn(false);

        int expiredCount = cleanupAppService.expireUploadSessions();

        assertEquals(2, expiredCount);
        verify(objectStoragePort, times(2)).resolveBucketName(AccessLevel.PUBLIC);
        verify(objectStoragePort).abortMultipartUpload(
                "public-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        );
        verify(objectStoragePort).deleteObject(
                "public-bucket",
                "blog/2026/03/14/user-001/uploads/session-ps-001-avatar.png"
        );
        verifyNoMoreInteractions(objectStoragePort);
        verify(uploadSessionRepository).updateStatus("session-001", UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepository).updateStatus("session-ps-001", UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepository).updateStatus("session-002", UploadSessionStatus.EXPIRED);
    }

    private UploadSession session(String uploadSessionId) {
        Instant now = Instant.parse("2026-03-14T01:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PUBLIC,
                "demo.mp4",
                "video/mp4",
                1024L,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                5 * 1024 * 1024,
                1,
                "provider-001",
                null,
                UploadSessionStatus.UPLOADING,
                now,
                now,
                now
        );
    }

    private UploadSession inlineSession(String uploadSessionId) {
        Instant now = Instant.parse("2026-03-14T01:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.INLINE,
                AccessLevel.PUBLIC,
                "note.txt",
                "text/plain",
                128L,
                null,
                null,
                0,
                0,
                null,
                null,
                UploadSessionStatus.INITIATED,
                now,
                now,
                now
        );
    }

    private UploadSession presignedSession(String uploadSessionId) {
        Instant now = Instant.parse("2026-03-14T01:00:00Z");
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PUBLIC,
                "avatar.png",
                "image/png",
                2048L,
                "hash-ps-001",
                "blog/2026/03/14/user-001/uploads/session-ps-001-avatar.png",
                0,
                1,
                null,
                null,
                UploadSessionStatus.UPLOADING,
                now,
                now,
                now
        );
    }
}
