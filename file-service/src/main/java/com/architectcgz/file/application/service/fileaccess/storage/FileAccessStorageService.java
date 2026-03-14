package com.architectcgz.file.application.service.fileaccess.storage;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 文件访问存储协调服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAccessStorageService {

    private final StorageService storageService;
    private final FileUrlCacheManager fileUrlCacheManager;

    @Setter
    private int privateUrlExpireSeconds = 3600;

    public String getCachedUrl(String fileId) {
        return fileUrlCacheManager.get(fileId);
    }

    public void cachePublicUrl(String fileId, String url) {
        fileUrlCacheManager.put(fileId, url);
    }

    public String resolveTargetBucketName(AccessLevel accessLevel) {
        return storageService.getBucketName(accessLevel);
    }

    public String resolvePublicBucketName() {
        return storageService.getBucketName(AccessLevel.PUBLIC);
    }

    public boolean isPublicBucket(String bucketName, String publicBucketName) {
        if (!StringUtils.hasText(publicBucketName)) {
            return !StringUtils.hasText(bucketName);
        }
        return publicBucketName.equals(bucketName);
    }

    public boolean isBucketMatchTarget(String currentBucketName, String targetBucketName) {
        if (!StringUtils.hasText(targetBucketName)) {
            return !StringUtils.hasText(currentBucketName);
        }
        return targetBucketName.equals(currentBucketName);
    }

    public String getPublicUrl(String bucketName, String storagePath) {
        return storageService.getPublicUrl(bucketName, storagePath);
    }

    public String generatePresignedUrl(String bucketName, String storagePath) {
        return storageService.generatePresignedUrl(
                bucketName,
                storagePath,
                Duration.ofSeconds(privateUrlExpireSeconds)
        );
    }

    public int getPrivateUrlExpireSeconds() {
        return privateUrlExpireSeconds;
    }

    public void copy(String sourceBucketName, String sourcePath, String targetBucketName, String targetPath) {
        storageService.copy(sourceBucketName, sourcePath, targetBucketName, targetPath);
    }

    public void deleteQuietly(String bucketName, String storagePath) {
        try {
            storageService.delete(bucketName, storagePath);
        } catch (Exception ex) {
            log.warn("Failed to delete storage object quietly: bucket={}, path={}", bucketName, storagePath, ex);
        }
    }

    public void registerAfterCommitCleanup(String fileId, boolean deleteSourceObject,
                                           String sourceBucketName, String sourcePath) {
        Runnable afterCommitAction = () -> {
            fileUrlCacheManager.evict(fileId);
            if (deleteSourceObject && StringUtils.hasText(sourcePath)) {
                deleteQuietly(sourceBucketName, sourcePath);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommitAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitAction.run();
            }
        });
    }
}
