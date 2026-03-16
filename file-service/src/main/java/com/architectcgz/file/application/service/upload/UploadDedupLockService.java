package com.architectcgz.file.application.service.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 文件去重锁服务。
 *
 * 针对同一 appId + fileHash + bucketName 的上传过程做分布式串行化，
 * 避免并发上传同一文件时重复写入对象存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadDedupLockService {

    private static final String LOCK_PREFIX = "file-service:dedup:";

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(String appId, String fileHash, String bucketName, Supplier<T> action) {
        String lockKey = buildLockKey(appId, fileHash, bucketName);
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildLockKey(String appId, String fileHash, String bucketName) {
        String resolvedBucket = bucketName == null ? "default" : bucketName;
        return LOCK_PREFIX + appId + ":" + resolvedBucket + ":" + fileHash;
    }
}
