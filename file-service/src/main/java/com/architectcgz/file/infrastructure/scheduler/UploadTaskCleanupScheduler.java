package com.architectcgz.file.infrastructure.scheduler;

import com.platform.fileservice.core.application.service.CleanupAppService;
import com.platform.fileservice.core.domain.model.UploadSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上传任务清理定时任务
 * 定期清理过期的未完成上传任务，释或S3 存储空间
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.multipart.enabled", havingValue = "true")
public class UploadTaskCleanupScheduler {

    private final CleanupAppService cleanupAppService;
    
    /**
     * 清理过期的上传任或
     * 按配置的 cron 表达式执行（默认每小时执行一次）
     */
    @Scheduled(cron = "${storage.multipart.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredTasks() {
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
        }
    }
}
