package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    private static final AccessLevel DEFAULT_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;
    
    private final UploadTaskRepository uploadTaskRepository;
    private final S3StorageService s3StorageService;
    private final MultipartProperties multipartProperties;
    
    /**
     * 清理过期的上传任或
     * 按配置的 cron 表达式执行（默认每小时执行一次）
     */
    @Scheduled(cron = "${storage.multipart.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredTasks() {
        log.info("Starting cleanup of expired upload tasks");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 查询所有过期的上传任务
            List<UploadTask> expiredTasks = uploadTaskRepository.findExpiredTasks(now);
            
            if (expiredTasks.isEmpty()) {
                log.info("No expired upload tasks found");
                return;
            }
            
            log.info("Found {} expired upload tasks to clean up", expiredTasks.size());
            
            int successCount = 0;
            int failureCount = 0;
            
            // 逐个清理过期任务
            for (UploadTask task : expiredTasks) {
                try {
                    cleanupTask(task);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to cleanup expired task: taskId={}, error={}", 
                            task.getId(), e.getMessage(), e);
                }
            }
            
            log.info("Cleanup completed: total={}, success={}, failure={}", 
                    expiredTasks.size(), successCount, failureCount);
            
        } catch (Exception e) {
            log.error("Error during cleanup of expired upload tasks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清理单个过期任务
     * 
     * @param task 过期的上传任或
     */
    private void cleanupTask(UploadTask task) {
        log.debug("Cleaning up expired task: taskId={}, userId={}, fileName={}, uploadId={}", 
                task.getId(), task.getUserId(), task.getFileName(), task.getUploadId());
        
        try {
            // 1. 调用 S3 abortMultipartUpload 清理未完成的分片
            s3StorageService.abortMultipartUpload(
                    task.getStoragePath(),
                    task.getUploadId(),
                    s3StorageService.getBucketName(DEFAULT_UPLOAD_ACCESS_LEVEL)
            );
            log.debug("S3 multipart upload aborted: taskId={}, uploadId={}", 
                    task.getId(), task.getUploadId());
            
        } catch (Exception e) {
            // S3 清理失败不影响数据库状态更或
            log.warn("Failed to abort S3 multipart upload: taskId={}, uploadId={}, error={}", 
                    task.getId(), task.getUploadId(), e.getMessage());
        }
        
        try {
            // 2. 更新任务状态为 EXPIRED
            uploadTaskRepository.updateStatus(task.getId(), UploadTaskStatus.EXPIRED);
            log.debug("Task status updated to EXPIRED: taskId={}", task.getId());
            
            // 3. 删除任务的所有分片记录（可选，保留记录便于追溯或
            // uploadTaskRepository.deletePartsByTaskId(task.getId());
            
        } catch (Exception e) {
            log.error("Failed to update task status: taskId={}, error={}", 
                    task.getId(), e.getMessage(), e);
            throw e;
        }
        
        log.info("Expired task cleaned up successfully: taskId={}, userId={}, fileName={}", 
                task.getId(), task.getUserId(), task.getFileName());
    }
}
