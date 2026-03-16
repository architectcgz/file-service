package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.infrastructure.config.UploadSessionCleanupProperties;
import com.platform.fileservice.core.application.service.CleanupAppService;
import com.platform.fileservice.core.domain.model.UploadSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 上传任务清理定时任务
 * 定期清理过期的未完成上传任务，释或S3 存储空间
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.multipart.enabled", havingValue = "true")
public class UploadTaskCleanupScheduler {

    private static final String CLEANUP_LOCK_KEY = "file-service:cleanup:upload-sessions:lock";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end",
            Long.class
    );

    private final CleanupAppService cleanupAppService;
    private final UploadSessionCleanupProperties uploadSessionCleanupProperties;
    private final StringRedisTemplate stringRedisTemplate;
    
    /**
     * 清理过期的上传任或
     * 按配置的 cron 表达式执行（默认每小时执行一次）
     */
    @Scheduled(cron = "${storage.multipart.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredTasks() {
        String lockValue = UUID.randomUUID().toString();
        Duration lockTimeout = Duration.ofSeconds(uploadSessionCleanupProperties.getLockTimeoutSeconds());
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(CLEANUP_LOCK_KEY, lockValue, lockTimeout);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("其他实例正在执行过期上传会话清理，跳过本次调度");
            return;
        }

        log.info("Starting cleanup of expired upload sessions");

        try {
            List<UploadSession> expiredSessions = cleanupAppService.findExpiredUploadSessions();

            if (expiredSessions.isEmpty()) {
                log.info("No expired upload sessions found");
                return;
            }

            log.info("Found {} expired upload sessions to clean up", expiredSessions.size());

            int successCount = 0;
            int failureCount = 0;

            for (UploadSession uploadSession : expiredSessions) {
                try {
                    if (cleanupAppService.expireUploadSession(uploadSession)) {
                        successCount++;
                    } else {
                        failureCount++;
                        log.warn("Expired upload session status update skipped: uploadSessionId={}",
                                uploadSession.uploadSessionId());
                    }
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to cleanup expired upload session: uploadSessionId={}, error={}",
                            uploadSession.uploadSessionId(), e.getMessage(), e);
                }
            }

            log.info("Cleanup completed: total={}, success={}, failure={}",
                    expiredSessions.size(), successCount, failureCount);

        } catch (Exception e) {
            log.error("Error during cleanup of expired upload sessions: {}", e.getMessage(), e);
        } finally {
            Long released = stringRedisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(CLEANUP_LOCK_KEY),
                    lockValue
            );
            if (!Long.valueOf(1L).equals(released)) {
                log.warn("上传会话清理锁释放失败，锁可能已被其他实例接管: key={}", CLEANUP_LOCK_KEY);
            }
        }
    }
}
