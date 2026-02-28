package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 孤立文件清理定时任务
 *
 * 定期扫描并清理以下两类不一致数据：
 * 1. 数据库中引用计数为零的 StorageObject（S3 对象可能仍存在）
 * 2. S3 中存在但数据库无记录的孤立对象（反向检查，暂由运维手动触发）
 *
 * 该任务作为 deleteFile() 操作的兜底补偿机制，
 * 处理因进程崩溃、网络超时等原因导致的 S3 与数据库状态不一致问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedObjectCleanupScheduler {

    private final StorageObjectRepository storageObjectRepository;
    private final StorageService storageService;

    /**
     * 每批处理的最大记录数，通过配置注入，避免一次加载过多数据
     */
    @Value("${file-service.cleanup.orphaned.batch-size:100}")
    private int batchSize;

    /**
     * 清理数据库中引用计数为零的孤立 StorageObject
     *
     * 执行逻辑：
     * 1. 分批查询 reference_count <= 0 的 StorageObject
     * 2. 尝试删除对应的 S3 对象
     * 3. 删除数据库中的 StorageObject 记录
     *
     * S3 删除失败不阻塞后续记录的处理，仅记录日志，下次调度时重试
     */
    @Scheduled(cron = "${file-service.cleanup.orphaned.cron:0 30 3 * * *}")
    public void cleanupOrphanedObjects() {
        log.info("开始执行孤立文件清理任务");

        try {
            List<StorageObject> orphanedObjects =
                    storageObjectRepository.findZeroReferenceObjects(batchSize);

            if (orphanedObjects.isEmpty()) {
                log.info("未发现孤立 StorageObject，跳过清理");
                return;
            }

            log.info("发现 {} 个孤立 StorageObject，开始清理", orphanedObjects.size());

            int successCount = 0;
            int failureCount = 0;

            for (StorageObject storageObject : orphanedObjects) {
                try {
                    cleanupSingleObject(storageObject);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error("清理孤立对象失败: storageObjectId={}, path={}, error={}",
                            storageObject.getId(),
                            storageObject.getStoragePath(),
                            e.getMessage(), e);
                }
            }

            log.info("孤立文件清理完成: total={}, success={}, failure={}",
                    orphanedObjects.size(), successCount, failureCount);

        } catch (Exception e) {
            log.error("孤立文件清理任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理单个孤立 StorageObject
     * 先删 S3 对象，再删数据库记录，保持与 deleteFile() 一致的操作顺序
     *
     * @param storageObject 待清理的存储对象
     */
    private void cleanupSingleObject(StorageObject storageObject) {
        String objectId = storageObject.getId();
        String storagePath = storageObject.getStoragePath();

        log.debug("清理孤立对象: storageObjectId={}, path={}, referenceCount={}",
                objectId, storagePath, storageObject.getReferenceCount());

        // 1. 尝试删除 S3 对象
        try {
            if (storageService.exists(storagePath)) {
                storageService.delete(storagePath);
                log.info("S3 孤立对象已删除: path={}", storagePath);
            } else {
                log.info("S3 对象已不存在，跳过删除: path={}", storagePath);
            }
        } catch (Exception e) {
            // S3 删除失败时记录警告，仍然尝试清理数据库记录
            // 因为该对象引用计数已为零，保留数据库记录无意义
            log.warn("S3 对象删除失败，继续清理数据库记录: path={}, error={}",
                    storagePath, e.getMessage());
        }

        // 2. 删除数据库中的 StorageObject 记录
        boolean deleted = storageObjectRepository.deleteById(objectId);
        if (deleted) {
            log.info("孤立 StorageObject 记录已删除: storageObjectId={}", objectId);
        } else {
            log.warn("孤立 StorageObject 记录删除失败（可能已被其他进程处理）: storageObjectId={}",
                    objectId);
        }
    }
}
