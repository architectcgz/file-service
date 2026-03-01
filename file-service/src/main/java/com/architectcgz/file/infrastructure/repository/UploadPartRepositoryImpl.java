package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.architectcgz.file.infrastructure.monitoring.BitmapMetrics;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.mapper.UploadTaskMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import com.architectcgz.file.infrastructure.repository.po.UploadTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分片上传仓储实现 - Bitmap 优化版本
 * 
 * 使用 Redis Bitmap 作为快速缓存层，配合定期同步和故障回退机制，
 * 将数据库写入操作减少 90% 以上，同时保证数据可靠性和一致性。
 * 
 * 核心特性:
 * - Redis Bitmap 记录分片状态（毫秒级性能）
 * - 定期异步同步到数据库（每 N 个分片）
 * - 完成时全量同步（确保数据完整性）
 * - Redis 故障自动回退到数据库（高可用）
 * - 断点续传支持（从数据库恢复状态）
 * 
 * @author architectcgz
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UploadPartRepositoryImpl implements UploadPartRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final UploadPartMapper uploadPartMapper;
    private final BitmapProperties bitmapProperties;
    private final BitmapMetrics metrics;
    private final UploadTaskMapper uploadTaskMapper;
    
    /**
     * 记录分片上传
     * 
     * 实现策略:
     * 1. 参数校验（分片编号范围、taskId 有效性）
     * 2. 检查 Bitmap 功能是否启用
     * 3. 检查是否需要降级（超大文件）
     * 4. 写入 Redis Bitmap (SETBIT)
     * 5. 设置 TTL (24 小时)
     * 6. 判断是否需要触发定期同步
     * 7. Redis 失败时回退到数据库
     * 
     * @param part 分片信息
     * @throws IllegalArgumentException 参数无效时抛出
     */
    @Override
    public void savePart(UploadPart part) {
        metrics.recordTiming("savePart", () -> {
            // 1. 参数校验
            validatePartParameters(part);
            
            // 2. 查询任务信息（用于降级判断）
            UploadTaskPO taskPO = uploadTaskMapper.selectById(part.getTaskId());
            if (taskPO == null) {
                throw new IllegalArgumentException(
                    String.format(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND_WITH_ID, part.getTaskId())
                );
            }
            
            // 3. 校验分片编号范围
            if (part.getPartNumber() < 1 || part.getPartNumber() > taskPO.getTotalParts()) {
                throw new IllegalArgumentException(
                    String.format("分片编号超出范围: partNumber=%d, 有效范围=[1, %d]", 
                        part.getPartNumber(), taskPO.getTotalParts())
                );
            }
            
            // 4. 检查是否需要降级（超大文件）
            if (shouldDegradeToDatabase(taskPO.getTotalParts())) {
                log.info("分片数超过限制，使用数据库模式: taskId={}, totalParts={}, maxParts={}", 
                    taskPO.getId(), taskPO.getTotalParts(), bitmapProperties.getMaxParts());
                savePartToDatabase(part);
                return null;
            }
            
            // 5. 检查 Bitmap 功能是否启用
            if (!bitmapProperties.isEnabled()) {
                // Bitmap 功能禁用，直接使用数据库
                log.debug("Bitmap 功能已禁用，使用数据库模式: taskId={}, partNumber={}", 
                    part.getTaskId(), part.getPartNumber());
                savePartToDatabase(part);
                return null;
            }
            
            try {
                // 6. 写入 Redis Bitmap
                String bitmapKey = UploadRedisKeys.partsBitmap(part.getTaskId());
                long bitOffset = UploadRedisKeys.getBitOffset(part.getPartNumber());
                
                Boolean previousValue = redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
                
                // 7. 设置 TTL (24 小时)
                redisTemplate.expire(bitmapKey, Duration.ofHours(bitmapProperties.getExpireHours()));
                
                // 记录写入成功
                metrics.recordWriteSuccess();
                
                log.debug("分片记录到 Bitmap: taskId={}, partNumber={}, bitOffset={}, previousValue={}", 
                    part.getTaskId(), part.getPartNumber(), bitOffset, previousValue);
                
                // 8. 判断是否需要触发定期同步
                if (shouldSync(part.getPartNumber())) {
                    log.debug("触发定期同步: taskId={}, partNumber={}, syncBatchSize={}", 
                        part.getTaskId(), part.getPartNumber(), bitmapProperties.getSyncBatchSize());
                    asyncSyncToDatabase(part.getTaskId());
                }
                
            } catch (DataAccessException e) {
                // Redis 失败，回退到数据库
                metrics.recordWriteFailure("connection_failure");
                metrics.recordFallback("redis_unavailable");
                
                log.warn("Bitmap 写入失败，回退到数据库: taskId={}, partNumber={}, error={}", 
                    part.getTaskId(), part.getPartNumber(), e.getMessage(), e);
                savePartToDatabase(part);
            } catch (Exception e) {
                // 其他异常也回退到数据库
                metrics.recordWriteFailure("unknown_error");
                metrics.recordFallback("exception");
                
                log.error("记录分片时发生异常，回退到数据库: taskId={}, partNumber={}", 
                    part.getTaskId(), part.getPartNumber(), e);
                savePartToDatabase(part);
            }
            
            return null;
        });
    }
    
    /**
     * 查询已完成的分片数量
     * 
     * 实现策略:
     * 1. 优先从 Redis Bitmap 查询 (BITCOUNT)
     * 2. Redis 失败时回退到数据库
     * 3. 记录缓存命中/未命中
     * 
     * @param taskId 任务ID
     * @return 已完成分片数量
     */
    @Override
    public int countCompletedParts(String taskId) {
        return metrics.recordTiming("countParts", () -> {
            if (!bitmapProperties.isEnabled()) {
                metrics.recordCacheMiss();
                return countFromDatabase(taskId);
            }
            
            try {
                String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
                
                Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
                    byte[] keyBytes = bitmapKey.getBytes();
                    return connection.bitCount(keyBytes);
                });
                
                if (count != null) {
                    metrics.recordCacheHit();
                    log.debug("从 Bitmap 查询分片数量: taskId={}, count={}", taskId, count);
                    return count.intValue();
                }
                
                metrics.recordCacheMiss();
                log.debug("Bitmap 不存在，从数据库查询: taskId={}", taskId);
                return countFromDatabase(taskId);
                
            } catch (DataAccessException e) {
                metrics.recordCacheMiss();
                metrics.recordFallback("redis_query_failure");
                log.warn("Bitmap 查询失败，回退到数据库: taskId={}, error={}", taskId, e.getMessage());
                return countFromDatabase(taskId);
            } catch (Exception e) {
                metrics.recordCacheMiss();
                metrics.recordFallback("exception");
                log.error("查询分片数量时发生异常，回退到数据库: taskId={}", taskId, e);
                return countFromDatabase(taskId);
            }
        });
    }
    
    /**
     * 查询已完成的分片编号列表
     * 
     * 实现策略:
     * 1. 使用 BITCOUNT 获取总数
     * 2. 遍历 Bitmap 获取所有已完成分片编号
     * 3. 优化遍历性能（提前终止）
     * 4. Redis 失败时回退到数据库
     * 
     * @param taskId 任务ID
     * @return 分片编号列表（从小到大排序）
     */
    @Override
    public List<Integer> findCompletedPartNumbers(String taskId) {
        return metrics.recordTiming("findParts", () -> {
            if (!bitmapProperties.isEnabled()) {
                metrics.recordCacheMiss();
                return findPartNumbersFromDatabase(taskId);
            }
            
            try {
                String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
                
                // 1. 获取总分片数，避免无效遍历
                Long bitCount = redisTemplate.execute((RedisCallback<Long>) connection -> {
                    byte[] keyBytes = bitmapKey.getBytes();
                    return connection.bitCount(keyBytes);
                });
                
                if (bitCount == null || bitCount == 0) {
                    metrics.recordCacheMiss();
                    log.debug("Bitmap 不存在或为空，从数据库查询: taskId={}", taskId);
                    return findPartNumbersFromDatabase(taskId);
                }
                
                // 2. 遍历 Bitmap 获取所有已完成分片编号
                List<Integer> completedParts = new ArrayList<>();
                int maxParts = bitmapProperties.getMaxParts();
                
                for (int i = 0; i < maxParts && completedParts.size() < bitCount; i++) {
                    Boolean bit = redisTemplate.opsForValue().getBit(bitmapKey, i);
                    if (Boolean.TRUE.equals(bit)) {
                        int partNumber = UploadRedisKeys.getPartNumber(i);
                        completedParts.add(partNumber);
                    }
                }
                
                metrics.recordCacheHit();
                log.debug("从 Bitmap 查询分片列表: taskId={}, count={}", taskId, completedParts.size());
                return completedParts;
                
            } catch (DataAccessException e) {
                metrics.recordCacheMiss();
                metrics.recordFallback("redis_query_failure");
                log.warn("Bitmap 查询失败，回退到数据库: taskId={}, error={}", taskId, e.getMessage());
                return findPartNumbersFromDatabase(taskId);
            } catch (Exception e) {
                metrics.recordCacheMiss();
                metrics.recordFallback("exception");
                log.error("查询分片列表时发生异常，回退到数据库: taskId={}", taskId, e);
                return findPartNumbersFromDatabase(taskId);
            }
        });
    }
    
    /**
     * 完成上传时全量同步到数据库
     * 
     * 实现策略:
     * 1. 从 Bitmap 获取所有分片
     * 2. 批量插入到数据库
     * 3. 删除 Bitmap 释放内存
     * 4. 失败时抛出异常并保留 Bitmap
     * 
     * @param taskId 任务ID
     * @param parts 所有分片信息
     * @throws RuntimeException 同步失败时抛出异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAllPartsToDatabase(String taskId, List<UploadPart> parts) {
        try {
            if (parts == null || parts.isEmpty()) {
                log.warn("全量同步分片列表为空: taskId={}", taskId);
                return;
            }
            
            // 1. 转换为 PO 对象
            List<UploadPartPO> partPOs = parts.stream()
                .map(this::convertToPO)
                .collect(Collectors.toList());
            
            // 2. 批量插入到数据库
            batchInsertParts(partPOs);
            
            log.info("全量同步分片记录成功: taskId={}, count={}", taskId, parts.size());
            
            // 3. 删除 Bitmap 释放内存
            if (bitmapProperties.isEnabled()) {
                String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
                Boolean deleted = redisTemplate.delete(bitmapKey);
                log.debug("删除 Bitmap: taskId={}, deleted={}", taskId, deleted);
            }
            
        } catch (Exception e) {
            log.error("全量同步失败，保留 Bitmap: taskId={}, count={}", taskId, parts.size(), e);
            throw new RuntimeException("同步分片记录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库加载分片状态到 Bitmap（用于断点续传）
     * 
     * 实现策略:
     * 1. 从数据库查询已完成分片
     * 2. 重建 Bitmap
     * 3. 设置 TTL
     * 
     * @param taskId 任务ID
     */
    @Override
    public void loadPartsFromDatabase(String taskId) {
        if (!bitmapProperties.isEnabled()) {
            log.debug("Bitmap 功能已禁用，跳过加载: taskId={}", taskId);
            return;
        }
        
        try {
            // 1. 从数据库查询已完成分片编号
            List<Integer> partNumbers = findPartNumbersFromDatabase(taskId);
            
            if (partNumbers.isEmpty()) {
                log.debug("数据库中没有分片记录: taskId={}", taskId);
                return;
            }
            
            // 2. 重建 Bitmap
            String bitmapKey = UploadRedisKeys.partsBitmap(taskId);
            
            for (Integer partNumber : partNumbers) {
                long bitOffset = UploadRedisKeys.getBitOffset(partNumber);
                redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
            }
            
            // 3. 设置 TTL
            redisTemplate.expire(bitmapKey, Duration.ofHours(bitmapProperties.getExpireHours()));
            
            log.info("从数据库加载分片状态到 Bitmap: taskId={}, count={}", taskId, partNumbers.size());
            
        } catch (Exception e) {
            log.error("加载分片状态失败: taskId={}", taskId, e);
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 校验分片参数
     * 
     * @param part 分片信息
     * @throws IllegalArgumentException 参数无效时抛出
     */
    private void validatePartParameters(UploadPart part) {
        if (part == null) {
            throw new IllegalArgumentException("分片信息不能为空");
        }
        
        if (part.getTaskId() == null || part.getTaskId().trim().isEmpty()) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        
        if (part.getPartNumber() == null) {
            throw new IllegalArgumentException("分片编号不能为空");
        }
        
        if (part.getPartNumber() < 1) {
            throw new IllegalArgumentException(
                String.format("分片编号必须大于0: partNumber=%d", part.getPartNumber())
            );
        }
    }
    
    /**
     * 判断是否需要降级到数据库模式
     * 
     * 当总分片数超过配置的最大值时，直接使用数据库模式
     * 避免 Bitmap 遍历性能问题和内存占用过大
     * 
     * @param totalParts 总分片数
     * @return true 如果需要降级
     */
    private boolean shouldDegradeToDatabase(Integer totalParts) {
        if (totalParts == null) {
            return false;
        }
        
        int maxParts = bitmapProperties.getMaxParts();
        return totalParts > maxParts;
    }
    
    /**
     * 判断是否需要同步
     * 
     * @param partNumber 分片编号
     * @return true 如果需要同步
     */
    private boolean shouldSync(int partNumber) {
        int syncBatchSize = bitmapProperties.getSyncBatchSize();
        return syncBatchSize > 0 && partNumber % syncBatchSize == 0;
    }
    
    /**
     * 异步同步到数据库
     * 
     * 实现策略:
     * 1. 比较 Bitmap 和数据库中的分片记录
     * 2. 找出差异（新分片）
     * 3. 批量插入到数据库
     * 4. 异常处理（记录日志但不抛出）
     * 
     * @param taskId 任务ID
     */
    @Async("bitmapSyncExecutor")
    public void asyncSyncToDatabase(String taskId) {
        try {
            // 1. 从 Bitmap 获取已完成分片
            List<Integer> bitmapParts = findCompletedPartNumbers(taskId);
            
            if (bitmapParts.isEmpty()) {
                log.debug("Bitmap 中没有分片记录，跳过同步: taskId={}", taskId);
                return;
            }
            
            // 2. 从数据库获取已有分片
            List<Integer> dbParts = findPartNumbersFromDatabase(taskId);
            
            // 3. 找出差异（新分片）
            List<Integer> newParts = bitmapParts.stream()
                .filter(partNumber -> !dbParts.contains(partNumber))
                .collect(Collectors.toList());
            
            if (newParts.isEmpty()) {
                log.debug("没有新分片需要同步: taskId={}", taskId);
                return;
            }
            
            // 4. 批量插入到数据库
            List<UploadPartPO> partsToInsert = newParts.stream()
                .map(partNumber -> {
                    UploadPartPO po = new UploadPartPO();
                    po.setId(generateId());
                    po.setTaskId(taskId);
                    po.setPartNumber(partNumber);
                    po.setUploadedAt(LocalDateTime.now());
                    return po;
                })
                .collect(Collectors.toList());
            
            batchInsertParts(partsToInsert);
            
            // 记录同步指标
            metrics.recordSync(newParts.size());
            
            log.info("异步同步分片成功: taskId={}, newCount={}, totalInBitmap={}, totalInDb={}", 
                taskId, newParts.size(), bitmapParts.size(), dbParts.size() + newParts.size());
            
        } catch (Exception e) {
            // 异步同步失败不影响主流程，只记录日志
            log.error("异步同步失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 直接写入数据库
     * 
     * @param part 分片信息
     */
    private void savePartToDatabase(UploadPart part) {
        try {
            UploadPartPO po = convertToPO(part);
            uploadPartMapper.insert(po);
            log.debug("分片记录到数据库: taskId={}, partNumber={}", part.getTaskId(), part.getPartNumber());
        } catch (Exception e) {
            log.error("写入数据库失败: taskId={}, partNumber={}", part.getTaskId(), part.getPartNumber(), e);
            throw new RuntimeException("写入数据库失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库查询分片数量
     * 
     * @param taskId 任务ID
     * @return 分片数量
     */
    private int countFromDatabase(String taskId) {
        try {
            int count = uploadPartMapper.countByTaskId(taskId);
            log.debug("从数据库查询分片数量: taskId={}, count={}", taskId, count);
            return count;
        } catch (Exception e) {
            log.error("从数据库查询分片数量失败: taskId={}", taskId, e);
            return 0;
        }
    }
    
    /**
     * 从数据库查询分片编号列表
     * 
     * @param taskId 任务ID
     * @return 分片编号列表
     */
    private List<Integer> findPartNumbersFromDatabase(String taskId) {
        try {
            List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(taskId);
            log.debug("从数据库查询分片列表: taskId={}, count={}", taskId, partNumbers.size());
            return partNumbers;
        } catch (Exception e) {
            log.error("从数据库查询分片列表失败: taskId={}", taskId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 批量插入分片记录
     * 
     * @param parts 分片列表
     */
    private void batchInsertParts(List<UploadPartPO> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        
        try {
            // 使用批量插入，ON CONFLICT DO NOTHING 保证幂等性
            uploadPartMapper.batchInsert(parts);
            log.debug("批量插入分片记录: count={}", parts.size());
        } catch (Exception e) {
            log.error("批量插入分片记录失败", e);
            throw new RuntimeException("批量插入分片记录失败", e);
        }
    }
    
    /**
     * 转换领域模型到持久化对象
     * 
     * @param part 领域模型
     * @return 持久化对象
     */
    private UploadPartPO convertToPO(UploadPart part) {
        UploadPartPO po = new UploadPartPO();
        po.setId(part.getId() != null ? part.getId() : generateId());
        po.setTaskId(part.getTaskId());
        po.setPartNumber(part.getPartNumber());
        po.setEtag(part.getEtag());
        po.setSize(part.getSize());
        po.setUploadedAt(part.getUploadedAt() != null ? part.getUploadedAt() : LocalDateTime.now());
        return po;
    }
    
    /**
     * 生成 ID (简化版本，实际应使用 UUIDv7)
     * 
     * @return ID
     */
    private String generateId() {
        return java.util.UUID.randomUUID().toString();
    }
}
