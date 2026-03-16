package com.architectcgz.file.application.service.uploadsession;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.UploadSessionInitProperties;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 在多实例下串行化同用户同 hash 的 upload-session 初始化，避免并发创建重复会话。
 */
@Service
@RequiredArgsConstructor
public class UploadSessionInitCoordinatorService {

    private static final String LOCK_PREFIX = "file-service:upload-session:init:";

    private final UploadAppService uploadAppService;
    private final RedissonClient redissonClient;
    private final UploadSessionInitProperties uploadSessionInitProperties;

    public UploadSessionCreationResult createSession(String tenantId,
                                                     String ownerId,
                                                     UploadMode uploadMode,
                                                     AccessLevel accessLevel,
                                                     String originalFilename,
                                                     String contentType,
                                                     long expectedSize,
                                                     String fileHash,
                                                     Duration ttl,
                                                     int chunkSizeBytes,
                                                     int maxParts) {
        if (!uploadSessionInitProperties.isEnabled() || fileHash == null || fileHash.isBlank()) {
            return doCreateSession(
                    tenantId,
                    ownerId,
                    uploadMode,
                    accessLevel,
                    originalFilename,
                    contentType,
                    expectedSize,
                    fileHash,
                    ttl,
                    chunkSizeBytes,
                    maxParts
            );
        }

        String lockKey = LOCK_PREFIX + tenantId + ":" + ownerId + ":" + fileHash;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = tryLock(lock, lockKey);
        if (!locked) {
            throw new BusinessException(
                    FileServiceErrorCodes.BUSINESS_ERROR,
                    "当前文件正在初始化上传会话，请稍后重试"
            );
        }

        try {
            return doCreateSession(
                    tenantId,
                    ownerId,
                    uploadMode,
                    accessLevel,
                    originalFilename,
                    contentType,
                    expectedSize,
                    fileHash,
                    ttl,
                    chunkSizeBytes,
                    maxParts
            );
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean tryLock(RLock lock, String lockKey) {
        try {
            return lock.tryLock(uploadSessionInitProperties.getWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    FileServiceErrorCodes.BUSINESS_ERROR,
                    "等待上传会话初始化锁被中断: " + lockKey,
                    ex
            );
        }
    }

    private UploadSessionCreationResult doCreateSession(String tenantId,
                                                        String ownerId,
                                                        UploadMode uploadMode,
                                                        AccessLevel accessLevel,
                                                        String originalFilename,
                                                        String contentType,
                                                        long expectedSize,
                                                        String fileHash,
                                                        Duration ttl,
                                                        int chunkSizeBytes,
                                                        int maxParts) {
        return uploadAppService.createSession(
                tenantId,
                ownerId,
                uploadMode,
                accessLevel,
                originalFilename,
                contentType,
                expectedSize,
                fileHash,
                ttl,
                chunkSizeBytes,
                maxParts
        );
    }
}
