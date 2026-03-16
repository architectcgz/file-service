package com.architectcgz.file.application.service.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 去重等待通知服务。
 *
 * Redis 只负责尽快唤醒等待方，真正结果仍以数据库中的 storage_object 为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadDedupNotificationService {

    private static final String TOPIC_PREFIX = "file-service:dedup-notify:";

    private final RedissonClient redissonClient;

    public WaitResult awaitResult(String appId, String fileHash, String bucketName, Duration timeout) {
        long waitMillis = Math.max(0L, timeout.toMillis());
        if (waitMillis == 0L) {
            return WaitResult.TIMED_OUT;
        }

        try {
            RTopic topic = redissonClient.getTopic(buildTopicKey(appId, fileHash, bucketName));
            CountDownLatch latch = new CountDownLatch(1);
            int listenerId = topic.addListener(String.class, (channel, message) -> latch.countDown());
            try {
                boolean notified = latch.await(waitMillis, TimeUnit.MILLISECONDS);
                return notified ? WaitResult.NOTIFIED : WaitResult.TIMED_OUT;
            } finally {
                topic.removeListener(listenerId);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return WaitResult.INTERRUPTED;
        } catch (Exception ex) {
            log.warn("Dedup notify unavailable, fallback to polling: appId={}, bucket={}, fileHash={}",
                    appId, bucketName, fileHash, ex);
            return WaitResult.UNAVAILABLE;
        }
    }

    public void publishCompleted(String appId, String fileHash, String bucketName) {
        publish(appId, fileHash, bucketName, "COMPLETED");
    }

    public void publishRetry(String appId, String fileHash, String bucketName) {
        publish(appId, fileHash, bucketName, "RETRY");
    }

    private void publish(String appId, String fileHash, String bucketName, String message) {
        try {
            redissonClient.getTopic(buildTopicKey(appId, fileHash, bucketName)).publish(message);
        } catch (Exception ex) {
            log.warn("Failed to publish dedup notification: appId={}, bucket={}, fileHash={}, message={}",
                    appId, bucketName, fileHash, message, ex);
        }
    }

    private String buildTopicKey(String appId, String fileHash, String bucketName) {
        return TOPIC_PREFIX + appId + ":" + bucketName + ":" + fileHash;
    }

    public enum WaitResult {
        NOTIFIED,
        TIMED_OUT,
        INTERRUPTED,
        UNAVAILABLE
    }
}
