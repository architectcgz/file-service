package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.config.CleanupProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 孤立文件清理定时任务
 *
 * 定期扫描并清理数据库中引用计数为零的 StorageObject（S3 对象可能仍存在）。
 * 该任务作为 deleteFile() 操作的兜底补偿机制，
 * 处理因进程崩溃、网络超时等原因导致的 S3 与数据库状态不一致问题。
 *
 * 设计要点：
 * 1. 使用 Redis 分布式锁防止多实例重复执行；锁 value 使用 UUID，释放时通过 Lua 脚本
 *    比较 value 后再删除，避免误删其他实例持有的锁
 * 2. 循环分批处理，设置单次调度最大处理总量上限
 * 3. 严格遵循"先删 S3 再删数据库"原则，S3 删除失败时保留记录待下次重试
 * 4. 查询时增加时间保护窗口，避免与正常删除流程互相干扰
 *
 * 覆盖范围说明：
 * 本任务仅能清理"数据库中 reference_count <= 0 的 StorageObject"场景，
 * 即 deleteFile() 流程中 S3 已删除但数据库记录未删除的情况。
 * 对于"数据库中 StorageObject 记录已被删除，但 S3 文件仍存在"的场景（如 S3 删除前进程崩溃），
 * 由于 deleteFile() 已调整为"先删 S3 再删库"，此类情况在正常流程下不会发生；
 * 若因极端异常出现，需通过 S3 与数据库的全量对账任务处理（超出本任务范围）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedObjectCleanupScheduler {

    private final StorageObjectRepository storageObjectRepository;
    private final StorageService storageService;
    private final CleanupProperties cleanupProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /** 分布式锁的 Redis Key */
    private static final String CLEANUP_LOCK_KEY = "file-service:cleanup:orphaned-objects:lock";

    /**
     * 释放分布式锁的 Lua 脚本
     * 比较 value 后再删除，防止误删其他实例持有的锁
     * 返回值：1 表示成功释放，0 表示 value 不匹配（锁已被其他实例持有）
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end",
            Long.class
    );

    /**
     * 清理数据库中引用计数为零的孤立 StorageObject
     *
     * 执行逻辑：
     * 1. 获取分布式锁（value 为 UUID），防止多实例重复执行
     * 2. 循环分批查询 reference_count <= 0 且超过保护窗口的 StorageObject
     * 3. 逐个尝试删除 S3 对象，成功后再删除数据库记录
     * 4. 达到最大处理总量或无更多数据时停止
     * 5. 通过 Lua 脚本安全释放锁，避免误删其他实例的锁
     */
    @Scheduled(cron = "${file-service.cleanup.orphaned.cron:0 30 3 * * *}")
    public void cleanupOrphanedObjects() {
        // 使用 UUID 作为锁 value，确保只有持锁实例才能释放锁
        String lockValue = UUID.randomUUID().toString();
        Duration lockTimeout = Duration.ofSeconds(cleanupProperties.getLockTimeoutSeconds());
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(CLEANUP_LOCK_KEY, lockValue, lockTimeout);

        if (!Boolean.TRUE.equals(locked)) {
            log.info("其他实例正在执行孤立文件清理，跳过本次调度");
            return;
        }

        try {
            doCleanup();
        } finally {
            // 通过 Lua 脚本比较 value 后再删除，防止误删其他实例持有的锁
            Long released = stringRedisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(CLEANUP_LOCK_KEY),
                    lockValue
            );
            if (!Long.valueOf(1L).equals(released)) {
                log.warn("分布式锁释放失败，锁可能已超时被其他实例获取: key={}", CLEANUP_LOCK_KEY);
            }
        }
    }

    /**
     * 执行清理逻辑：循环分批处理，直到无更多数据或达到最大处理总量
     */
    private void doCleanup() {
        log.info("开始执行孤立文件清理任务");

        int batchSize = cleanupProperties.getBatchSize();
        int maxTotal = cleanupProperties.getMaxTotal();
        int graceMinutes = cleanupProperties.getGraceMinutes();

        int totalProcessed = 0;
        int successCount = 0;
        int failureCount = 0;

        try {
            while (totalProcessed < maxTotal) {
                List<StorageObject> batch = storageObjectRepository
                        .findZeroReferenceObjects(graceMinutes, batchSize);

                if (batch.isEmpty()) {
                    break;
                }

                for (StorageObject storageObject : batch) {
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
                    totalProcessed++;
                }
            }

            log.info("孤立文件清理完成: totalProcessed={}, success={}, failure={}",
                    totalProcessed, successCount, failureCount);

        } catch (Exception e) {
            log.error("孤立文件清理任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理单个孤立 StorageObject
     * 严格遵循"先删 S3 再删数据库"原则：S3 删除失败时保留数据库记录，等待下次重试
     *
     * @param storageObject 待清理的存储对象
     */
    private void cleanupSingleObject(StorageObject storageObject) {
        String objectId = storageObject.getId();
        String storagePath = storageObject.getStoragePath();

        log.debug("清理孤立对象: storageObjectId={}, path={}, referenceCount={}",
                objectId, storagePath, storageObject.getReferenceCount());

        // 1. 直接删除 S3 对象（S3 deleteObject 本身是幂等的，无需先 exists 检查）
        try {
            storageService.delete(storagePath);
            log.info("S3 孤立对象已删除: path={}", storagePath);
        } catch (Exception e) {
            // S3 删除失败时保留数据库记录，等待下次调度重试
            log.warn("S3 对象删除失败，保留数据库记录待下次重试: path={}, error={}",
                    storagePath, e.getMessage());
            return;
        }

        // 2. S3 删除成功后才删除数据库记录
        boolean deleted = storageObjectRepository.deleteById(objectId);
        if (deleted) {
            log.info("孤立 StorageObject 记录已删除: storageObjectId={}", objectId);
        } else {
            log.warn("孤立 StorageObject 记录删除失败（可能已被其他进程处理）: storageObjectId={}",
                    objectId);
        }
    }
}
