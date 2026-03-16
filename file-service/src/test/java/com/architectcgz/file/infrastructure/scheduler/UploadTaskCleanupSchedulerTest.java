package com.architectcgz.file.infrastructure.scheduler;

import com.platform.fileservice.core.application.service.CleanupAppService;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadTaskCleanupSchedulerTest {

    @Mock
    private CleanupAppService cleanupAppService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private com.architectcgz.file.infrastructure.config.UploadSessionCleanupProperties uploadSessionCleanupProperties;

    private UploadTaskCleanupScheduler scheduler;

    private UploadSession expiredSession1;
    private UploadSession expiredSession2;

    @BeforeEach
    void setUp() {
        scheduler = new UploadTaskCleanupScheduler(cleanupAppService, uploadSessionCleanupProperties, redissonClient);
        lenient().when(redissonClient.getLock("file-service:cleanup:upload-sessions:lock")).thenReturn(lock);
        lenient().when(lock.tryLock()).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        expiredSession1 = buildSession("session-1", "provider-1");
        expiredSession2 = buildSession("session-2", "provider-2");
    }

    @Test
    void testCleanupExpiredTasks_NoExpiredTasks() {
        when(cleanupAppService.findExpiredUploadSessions()).thenReturn(List.of());

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService).findExpiredUploadSessions();
        verify(cleanupAppService, never()).expireUploadSession(any());
        verify(lock).unlock();
    }

    @Test
    void testCleanupExpiredTasks_SkipsWhenLockHeldByOtherInstance() {
        when(lock.tryLock()).thenReturn(false);

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService, never()).findExpiredUploadSessions();
        verify(lock, never()).unlock();
    }

    @Test
    void testCleanupExpiredTasks_Success() {
        when(cleanupAppService.findExpiredUploadSessions()).thenReturn(List.of(expiredSession1, expiredSession2));
        when(cleanupAppService.expireUploadSession(expiredSession1)).thenReturn(true);
        when(cleanupAppService.expireUploadSession(expiredSession2)).thenReturn(true);

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService).findExpiredUploadSessions();
        verify(cleanupAppService).expireUploadSession(expiredSession1);
        verify(cleanupAppService).expireUploadSession(expiredSession2);
        verify(lock).unlock();
    }

    @Test
    void testCleanupExpiredTasks_SingleSessionFails() {
        when(cleanupAppService.findExpiredUploadSessions()).thenReturn(List.of(expiredSession1, expiredSession2));
        doThrow(new RuntimeException("storage error"))
                .when(cleanupAppService)
                .expireUploadSession(expiredSession1);
        when(cleanupAppService.expireUploadSession(expiredSession2)).thenReturn(true);

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService).expireUploadSession(expiredSession1);
        verify(cleanupAppService).expireUploadSession(expiredSession2);
        verify(lock).unlock();
    }

    @Test
    void testCleanupExpiredTasks_StatusUpdateSkipped() {
        when(cleanupAppService.findExpiredUploadSessions()).thenReturn(List.of(expiredSession1));
        when(cleanupAppService.expireUploadSession(expiredSession1)).thenReturn(false);

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService).expireUploadSession(expiredSession1);
        verify(lock).unlock();
    }

    @Test
    void testCleanupExpiredTasks_QueryFails() {
        when(cleanupAppService.findExpiredUploadSessions())
                .thenThrow(new RuntimeException("Database connection error"));

        scheduler.cleanupExpiredTasks();

        verify(cleanupAppService).findExpiredUploadSessions();
        verify(cleanupAppService, never()).expireUploadSession(any());
        verify(lock).unlock();
    }

    private UploadSession buildSession(String sessionId, String providerUploadId) {
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
                providerUploadId,
                null,
                UploadSessionStatus.UPLOADING,
                now,
                now,
                now.minusSeconds(60)
        );
    }
}
