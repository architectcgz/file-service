package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.config.CleanupProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanedObjectCleanupSchedulerTest {

    @Mock
    private StorageObjectRepository storageObjectRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private CleanupProperties cleanupProperties;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private OrphanedObjectCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrphanedObjectCleanupScheduler(
                storageObjectRepository,
                storageService,
                cleanupProperties,
                redissonClient
        );
        lenient().when(redissonClient.getLock("file-service:cleanup:orphaned-objects:lock")).thenReturn(lock);
        lenient().when(lock.tryLock()).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        lenient().when(cleanupProperties.getBatchSize()).thenReturn(100);
        lenient().when(cleanupProperties.getMaxTotal()).thenReturn(1000);
        lenient().when(cleanupProperties.getGraceMinutes()).thenReturn(60);
        lenient().when(cleanupProperties.getLockTimeoutSeconds()).thenReturn(1800L);
    }

    @Test
    void shouldSkipCleanupWhenLockHeldByOtherInstance() {
        when(lock.tryLock()).thenReturn(false);

        scheduler.cleanupOrphanedObjects();

        verify(storageObjectRepository, never()).findZeroReferenceObjects(60, 100);
        verify(lock, never()).unlock();
    }

    @Test
    void shouldCleanupBatchAndReleaseLock() {
        StorageObject storageObject = new StorageObject();
        storageObject.setId("storage-1");
        storageObject.setBucketName("bucket-a");
        storageObject.setStoragePath("tenant/files/a.txt");
        storageObject.setReferenceCount(0);
        storageObject.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));

        when(storageObjectRepository.findZeroReferenceObjects(60, 100))
                .thenReturn(List.of(storageObject))
                .thenReturn(List.of());
        when(storageObjectRepository.deleteById("storage-1")).thenReturn(true);

        scheduler.cleanupOrphanedObjects();

        verify(storageService).delete("bucket-a", "tenant/files/a.txt");
        verify(storageObjectRepository).deleteById("storage-1");
        verify(lock).unlock();
    }
}
