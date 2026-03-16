package com.architectcgz.file.application.service.upload;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadDedupClaimRepository;
import com.architectcgz.file.infrastructure.config.UploadDedupProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 上传去重协调服务。
 *
 * 通过数据库短 claim 抢占“同 hash 首个上传者”，
 * 将真正的对象上传移到 claim 外执行，缩小去重串行区。
 */
@Slf4j
@Service
public class UploadDedupCoordinatorService {

    private static final String DEFAULT_BUCKET_NAME = "default";

    private final StorageObjectRepository storageObjectRepository;
    private final UploadDedupClaimRepository uploadDedupClaimRepository;
    private final UploadDedupProperties uploadDedupProperties;
    private final UploadDedupNotificationService uploadDedupNotificationService;
    private final ScheduledExecutorService claimRenewScheduler;

    @Autowired
    public UploadDedupCoordinatorService(StorageObjectRepository storageObjectRepository,
                                         UploadDedupClaimRepository uploadDedupClaimRepository,
                                         UploadDedupProperties uploadDedupProperties,
                                         UploadDedupNotificationService uploadDedupNotificationService) {
        this(
                storageObjectRepository,
                uploadDedupClaimRepository,
                uploadDedupProperties,
                uploadDedupNotificationService,
                createClaimRenewScheduler(uploadDedupProperties.getRenewSchedulerThreads())
        );
    }

    UploadDedupCoordinatorService(StorageObjectRepository storageObjectRepository,
                                  UploadDedupClaimRepository uploadDedupClaimRepository,
                                  UploadDedupProperties uploadDedupProperties,
                                  UploadDedupNotificationService uploadDedupNotificationService,
                                  ScheduledExecutorService claimRenewScheduler) {
        this.storageObjectRepository = storageObjectRepository;
        this.uploadDedupClaimRepository = uploadDedupClaimRepository;
        this.uploadDedupProperties = uploadDedupProperties;
        this.uploadDedupNotificationService = uploadDedupNotificationService;
        this.claimRenewScheduler = claimRenewScheduler;
    }

    public <T> T executeWithDedupClaim(String appId,
                                       String fileHash,
                                       String bucketName,
                                       Function<StorageObject, T> existingUploadAction,
                                       Supplier<T> newUploadAction) {
        Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHashAndBucket(
                appId,
                fileHash,
                bucketName
        );
        if (existingStorageObject.isPresent()) {
            return existingUploadAction.apply(existingStorageObject.get());
        }

        String resolvedBucketName = normalizeBucketName(bucketName);
        String ownerToken = UUID.randomUUID().toString();
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plus(uploadDedupProperties.getWaitTimeout());

        while (true) {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(uploadDedupProperties.getClaimLease());
            if (uploadDedupClaimRepository.tryAcquireClaim(
                    appId,
                    fileHash,
                    resolvedBucketName,
                    ownerToken,
                    expiresAt
            )) {
                ClaimRenewHandle renewHandle = startClaimRenewal(appId, fileHash, resolvedBucketName, ownerToken);
                boolean completed = false;
                try {
                    Optional<StorageObject> latestStorageObject = storageObjectRepository.findByFileHashAndBucket(
                            appId,
                            fileHash,
                            bucketName
                    );
                    if (latestStorageObject.isPresent()) {
                        T result = existingUploadAction.apply(latestStorageObject.get());
                        completed = true;
                        return result;
                    }
                    T result = newUploadAction.get();
                    completed = true;
                    return result;
                } finally {
                    renewHandle.close();
                    uploadDedupClaimRepository.releaseClaim(appId, fileHash, resolvedBucketName, ownerToken);
                    if (completed) {
                        uploadDedupNotificationService.publishCompleted(appId, fileHash, resolvedBucketName);
                    } else {
                        uploadDedupNotificationService.publishRetry(appId, fileHash, resolvedBucketName);
                    }
                }
            }

            Optional<StorageObject> latestStorageObject = storageObjectRepository.findByFileHashAndBucket(
                    appId,
                    fileHash,
                    bucketName
            );
            if (latestStorageObject.isPresent()) {
                return existingUploadAction.apply(latestStorageObject.get());
            }

            if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(deadline)) {
                log.warn("Timed out waiting for deduplicated upload to finish: appId={}, bucket={}, fileHash={}",
                        appId, resolvedBucketName, fileHash);
                throw new BusinessException(
                        FileServiceErrorCodes.WAIT_DEDUP_UPLOAD_TIMEOUT,
                        FileServiceErrorMessages.WAIT_DEDUP_UPLOAD_TIMEOUT
                );
            }

            waitForClaimResult(appId, fileHash, resolvedBucketName, deadline);
        }
    }

    private void waitForNextPoll() {
        Duration pollInterval = uploadDedupProperties.getPollInterval();
        long sleepMillis = Math.max(1L, pollInterval.toMillis());
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    FileServiceErrorCodes.WAIT_DEDUP_UPLOAD_INTERRUPTED,
                    FileServiceErrorMessages.WAIT_DEDUP_UPLOAD_INTERRUPTED,
                    ex
            );
        }
    }

    private void waitForClaimResult(String appId, String fileHash, String bucketName, OffsetDateTime deadline) {
        Duration remaining = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), deadline);
        Duration notificationWait = remaining.compareTo(uploadDedupProperties.getNotificationMaxWait()) > 0
                ? uploadDedupProperties.getNotificationMaxWait()
                : remaining;
        if (notificationWait.isNegative() || notificationWait.isZero()) {
            return;
        }

        UploadDedupNotificationService.WaitResult waitResult = uploadDedupNotificationService.awaitResult(
                appId,
                fileHash,
                bucketName,
                notificationWait
        );
        if (waitResult == UploadDedupNotificationService.WaitResult.INTERRUPTED) {
            throw new BusinessException(
                    FileServiceErrorCodes.WAIT_DEDUP_UPLOAD_INTERRUPTED,
                    FileServiceErrorMessages.WAIT_DEDUP_UPLOAD_INTERRUPTED
            );
        }
        if (waitResult == UploadDedupNotificationService.WaitResult.UNAVAILABLE) {
            waitForNextPoll();
        }
    }

    private String normalizeBucketName(String bucketName) {
        return bucketName == null || bucketName.isBlank() ? DEFAULT_BUCKET_NAME : bucketName;
    }

    private ClaimRenewHandle startClaimRenewal(String appId, String fileHash, String bucketName, String ownerToken) {
        long intervalMillis = Math.max(1L, uploadDedupProperties.getRenewInterval().toMillis());
        ScheduledFuture<?> future = claimRenewScheduler.scheduleWithFixedDelay(
                () -> renewClaim(appId, fileHash, bucketName, ownerToken),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
        return () -> future.cancel(false);
    }

    private void renewClaim(String appId, String fileHash, String bucketName, String ownerToken) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(uploadDedupProperties.getClaimLease());
        boolean renewed = uploadDedupClaimRepository.renewClaim(appId, fileHash, bucketName, ownerToken, expiresAt);
        if (!renewed) {
            log.warn("Failed to renew dedup claim ownership: appId={}, bucket={}, fileHash={}",
                    appId, bucketName, fileHash);
        }
    }

    private static ScheduledExecutorService createClaimRenewScheduler(int threadCount) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                Math.max(1, threadCount),
                dedupRenewThreadFactory()
        );
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static ThreadFactory dedupRenewThreadFactory() {
        return runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("upload-dedup-renew-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdownClaimRenewScheduler() {
        claimRenewScheduler.shutdownNow();
    }

    @FunctionalInterface
    private interface ClaimRenewHandle {
        void close();
    }
}
