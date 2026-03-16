package com.architectcgz.file.application.service.upload;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadDedupClaimRepository;
import com.architectcgz.file.infrastructure.config.UploadDedupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadDedupCoordinatorService 单元测试")
class UploadDedupCoordinatorServiceTest {

    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private UploadDedupClaimRepository uploadDedupClaimRepository;
    @Mock
    private UploadDedupNotificationService uploadDedupNotificationService;

    private UploadDedupCoordinatorService uploadDedupCoordinatorService;

    @BeforeEach
    void setUp() {
        UploadDedupProperties uploadDedupProperties = new UploadDedupProperties();
        uploadDedupProperties.setClaimLease(Duration.ofMinutes(5));
        uploadDedupProperties.setWaitTimeout(Duration.ofMillis(30));
        uploadDedupProperties.setPollInterval(Duration.ofMillis(1));
        uploadDedupProperties.setNotificationMaxWait(Duration.ofMillis(5));
        uploadDedupCoordinatorService = new UploadDedupCoordinatorService(
                storageObjectRepository,
                uploadDedupClaimRepository,
                uploadDedupProperties,
                uploadDedupNotificationService
        );
    }

    @Test
    @DisplayName("已有存储对象时应直接走复用路径")
    void executeWithDedupClaim_shouldReuseExistingStorageObjectImmediately() {
        StorageObject storageObject = StorageObject.builder().id("storage-001").build();
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-001", "bucket-a"))
                .thenReturn(Optional.of(storageObject));

        String actual = uploadDedupCoordinatorService.executeWithDedupClaim(
                "blog",
                "hash-001",
                "bucket-a",
                existing -> "instant-" + existing.getId(),
                () -> "new-upload"
        );

        assertThat(actual).isEqualTo("instant-storage-001");
        verify(uploadDedupClaimRepository, never()).tryAcquireClaim(anyString(), anyString(), anyString(), anyString(), any());
        verify(uploadDedupNotificationService, never()).publishCompleted(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("拿到 claim 时应在锁外执行新上传并释放占位")
    void executeWithDedupClaim_shouldRunNewUploadAfterClaimAcquired() {
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-001", "bucket-a"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(uploadDedupClaimRepository.tryAcquireClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class)))
                .thenReturn(true);

        String actual = uploadDedupCoordinatorService.executeWithDedupClaim(
                "blog",
                "hash-001",
                "bucket-a",
                existing -> "instant",
                () -> "new-upload"
        );

        assertThat(actual).isEqualTo("new-upload");
        verify(uploadDedupClaimRepository).releaseClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString());
        verify(uploadDedupNotificationService).publishCompleted(eq("blog"), eq("hash-001"), eq("bucket-a"));
    }

    @Test
    @DisplayName("长上传期间应周期性续租 claim")
    void executeWithDedupClaim_shouldRenewClaimDuringLongRunningUpload() throws Exception {
        UploadDedupProperties uploadDedupProperties = new UploadDedupProperties();
        uploadDedupProperties.setClaimLease(Duration.ofSeconds(5));
        uploadDedupProperties.setWaitTimeout(Duration.ofMillis(100));
        uploadDedupProperties.setPollInterval(Duration.ofMillis(1));
        uploadDedupProperties.setRenewInterval(Duration.ofMillis(10));
        uploadDedupProperties.setNotificationMaxWait(Duration.ofMillis(5));
        uploadDedupCoordinatorService = new UploadDedupCoordinatorService(
                storageObjectRepository,
                uploadDedupClaimRepository,
                uploadDedupProperties,
                uploadDedupNotificationService
        );

        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-001", "bucket-a"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(uploadDedupClaimRepository.tryAcquireClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class)))
                .thenReturn(true);
        when(uploadDedupClaimRepository.renewClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class)))
                .thenReturn(true);

        String actual = uploadDedupCoordinatorService.executeWithDedupClaim(
                "blog",
                "hash-001",
                "bucket-a",
                existing -> "instant",
                () -> {
                    try {
                        Thread.sleep(35L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "new-upload";
                }
        );

        assertThat(actual).isEqualTo("new-upload");
        verify(uploadDedupClaimRepository, atLeastOnce())
                .renewClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class));
        verify(uploadDedupClaimRepository).releaseClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString());
        verify(uploadDedupNotificationService).publishCompleted(eq("blog"), eq("hash-001"), eq("bucket-a"));
    }

    @Test
    @DisplayName("自己未拿到 claim 但别人已完成上传时应等待后复用")
    void executeWithDedupClaim_shouldWaitAndReuseWhenAnotherUploaderCompletes() {
        StorageObject storageObject = StorageObject.builder().id("storage-002").build();
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-001", "bucket-a"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(storageObject));
        when(uploadDedupClaimRepository.tryAcquireClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(uploadDedupNotificationService.awaitResult(eq("blog"), eq("hash-001"), eq("bucket-a"), any(Duration.class)))
                .thenReturn(UploadDedupNotificationService.WaitResult.NOTIFIED);

        String actual = uploadDedupCoordinatorService.executeWithDedupClaim(
                "blog",
                "hash-001",
                "bucket-a",
                existing -> "instant-" + existing.getId(),
                () -> "new-upload"
        );

        assertThat(actual).isEqualTo("instant-storage-002");
        verify(uploadDedupClaimRepository, never()).releaseClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString());
        verify(uploadDedupNotificationService, never()).publishCompleted(eq("blog"), eq("hash-001"), eq("bucket-a"));
    }

    @Test
    @DisplayName("长时间未拿到 claim 且未看到结果时应超时失败")
    void executeWithDedupClaim_shouldTimeoutWhenClaimNeverResolves() {
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-001", "bucket-a"))
                .thenReturn(Optional.empty());
        when(uploadDedupClaimRepository.tryAcquireClaim(eq("blog"), eq("hash-001"), eq("bucket-a"), anyString(), any(LocalDateTime.class)))
                .thenReturn(false);
        when(uploadDedupNotificationService.awaitResult(eq("blog"), eq("hash-001"), eq("bucket-a"), any(Duration.class)))
                .thenReturn(UploadDedupNotificationService.WaitResult.TIMED_OUT);

        assertThatThrownBy(() -> uploadDedupCoordinatorService.executeWithDedupClaim(
                "blog",
                "hash-001",
                "bucket-a",
                existing -> "instant",
                () -> "new-upload"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("等待同内容文件上传结果超时");
    }
}
