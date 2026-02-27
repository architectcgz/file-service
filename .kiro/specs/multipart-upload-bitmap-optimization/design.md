# Design Document: 分片上传 Bitmap 优化

## Overview

本设计文档描述了如何使用 Redis Bitmap 优化分片上传系统的性能。当前系统将每个分片记录都写入 PostgreSQL 数据库，导致大文件上传时产生大量数据库 I/O 操作。通过引入 Redis Bitmap 作为快速缓存层，配合定期同步和故障回退机制，可以将数据库写入操作减少 90% 以上，同时保证数据可靠性和一致性。

### 核心设计理念

1. **性能优先**：使用 Redis Bitmap 提供毫秒级的分片状态记录和查询
2. **可靠性保证**：通过定期同步和完成时全量同步确保数据持久化
3. **故障容错**：Redis 不可用时自动降级到数据库模式
4. **配置化**：支持通过配置灵活控制功能开关和参数

### 性能提升预期

| 指标 | 当前方案 | Bitmap 方案 | 提升 |
|------|---------|------------|------|
| 记录 1000 个分片 | ~10 秒 | ~0.1 秒 | 100倍 |
| 查询上传进度 | ~0.5 秒 | ~0.001 秒 | 500倍 |
| 存储空间 | ~50 KB | 125 字节 | 400倍 |
| 数据库写入次数 | 1000 次 | ~100 次 | 10倍 |

## Architecture

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      Client (上传客户端)                     │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP/HTTPS
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  File Service API Layer                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  MultipartUploadController                            │  │
│  │  - initiate()                                         │  │
│  │  - uploadPart()                                       │  │
│  │  - complete()                                         │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                 Application Service Layer                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  MultipartUploadService                               │  │
│  │  - createUploadTask()                                 │  │
│  │  - recordPartUpload()                                 │  │
│  │  - completeUpload()                                   │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer (核心)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  UploadPartRepository (接口)                          │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  UploadPartRepositoryImpl (实现)                      │  │
│  │                                                        │  │
│  │  ┌──────────────┐         ┌──────────────┐          │  │
│  │  │ Redis Bitmap │ (优先)  │  PostgreSQL  │ (回退)   │  │
│  │  │              │────────▶│              │          │  │
│  │  │ - SETBIT     │         │ - INSERT     │          │  │
│  │  │ - GETBIT     │         │ - SELECT     │          │  │
│  │  │ - BITCOUNT   │         │ - COUNT      │          │  │
│  │  └──────────────┘         └──────────────┘          │  │
│  │                                                        │  │
│  │  同步策略:                                             │  │
│  │  1. 每 N 个分片异步同步                                │  │
│  │  2. 完成时全量同步                                     │  │
│  │  3. Redis 失败立即回退                                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 数据流设计

#### 1. 分片上传流程

```
Client                Service              Repository           Redis        PostgreSQL
  │                     │                      │                  │               │
  │──uploadPart()──────▶│                      │                  │               │
  │                     │                      │                  │               │
  │                     │──savePart()─────────▶│                  │               │
  │                     │                      │                  │               │
  │                     │                      │──SETBIT─────────▶│               │
  │                     │                      │◀─────OK──────────│               │
  │                     │                      │                  │               │
  │                     │                      │ (每 N 个分片)    │               │
  │                     │                      │──asyncSync()────────────────────▶│
  │                     │                      │                  │               │
  │                     │◀─────success─────────│                  │               │
  │◀────200 OK──────────│                      │                  │               │
```

#### 2. Redis 故障回退流程

```
Client                Service              Repository           Redis        PostgreSQL
  │                     │                      │                  │               │
  │──uploadPart()──────▶│                      │                  │               │
  │                     │                      │                  │               │
  │                     │──savePart()─────────▶│                  │               │
  │                     │                      │                  │               │
  │                     │                      │──SETBIT─────────▶│               │
  │                     │                      │◀────ERROR────────│ (连接失败)    │
  │                     │                      │                  │               │
  │                     │                      │ (自动回退)       │               │
  │                     │                      │──INSERT─────────────────────────▶│
  │                     │                      │◀────────────────────────OK───────│
  │                     │                      │                  │               │
  │                     │◀─────success─────────│                  │               │
  │◀────200 OK──────────│                      │                  │               │
```

#### 3. 上传完成同步流程

```
Client                Service              Repository           Redis        PostgreSQL
  │                     │                      │                  │               │
  │──complete()────────▶│                      │                  │               │
  │                     │                      │                  │               │
  │                     │──syncAllParts()─────▶│                  │               │
  │                     │                      │                  │               │
  │                     │                      │──BITCOUNT───────▶│               │
  │                     │                      │◀─────1000────────│               │
  │                     │                      │                  │               │
  │                     │                      │──遍历 Bitmap─────▶│               │
  │                     │                      │◀─[1,2,3...1000]──│               │
  │                     │                      │                  │               │
  │                     │                      │──BATCH INSERT───────────────────▶│
  │                     │                      │◀────────────────────────OK───────│
  │                     │                      │                  │               │
  │                     │                      │──DEL key────────▶│               │
  │                     │                      │◀─────OK──────────│               │
  │                     │                      │                  │               │
  │                     │◀─────success─────────│                  │               │
  │◀────200 OK──────────│                      │                  │               │
```

## Components and Interfaces

### 1. UploadPartRepository 接口

```java
/**
 * 分片上传仓储接口
 * 负责分片状态的记录、查询和同步
 */
public interface UploadPartRepository {
    
    /**
     * 记录分片上传
     * 优先使用 Redis Bitmap，失败时回退到数据库
     * 
     * @param part 分片信息
     */
    void savePart(UploadPart part);
    
    /**
     * 查询已完成的分片数量
     * 优先从 Redis Bitmap 查询，失败时查询数据库
     * 
     * @param taskId 任务ID
     * @return 已完成分片数量
     */
    int countCompletedParts(String taskId);
    
    /**
     * 查询已完成的分片编号列表
     * 优先从 Redis Bitmap 查询，失败时查询数据库
     * 
     * @param taskId 任务ID
     * @return 分片编号列表
     */
    List<Integer> findCompletedPartNumbers(String taskId);
    
    /**
     * 完成上传时全量同步到数据库
     * 
     * @param taskId 任务ID
     * @param parts 所有分片信息
     */
    void syncAllPartsToDatabase(String taskId, List<UploadPart> parts);
    
    /**
     * 从数据库加载分片状态到 Bitmap（用于断点续传）
     * 
     * @param taskId 任务ID
     */
    void loadPartsFromDatabase(String taskId);
}
```

### 2. UploadPartRepositoryImpl 实现

```java
/**
 * 分片上传仓储实现 - Bitmap 优化版本
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadPartRepositoryImpl implements UploadPartRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final UploadPartMapper uploadPartMapper;
    private final BitmapProperties bitmapProperties;
    
    private static final String BITMAP_KEY_PREFIX = "upload:task:";
    private static final String BITMAP_KEY_SUFFIX = ":parts";
    
    @Override
    public void savePart(UploadPart part) {
        if (!bitmapProperties.isEnabled()) {
            // Bitmap 功能禁用，直接使用数据库
            savePartToDatabase(part);
            return;
        }
        
        try {
            // 1. 写入 Redis Bitmap
            String bitmapKey = getBitmapKey(part.getTaskId());
            int bitOffset = part.getPartNumber() - 1;
            
            redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
            redisTemplate.expire(bitmapKey, 
                Duration.ofHours(bitmapProperties.getExpireHours()));
            
            log.debug("分片记录到 Bitmap: taskId={}, partNumber={}", 
                part.getTaskId(), part.getPartNumber());
            
            // 2. 定期同步到数据库
            if (shouldSync(part.getPartNumber())) {
                asyncSyncToDatabase(part.getTaskId());
            }
            
        } catch (Exception e) {
            // Redis 失败，回退到数据库
            log.warn("Bitmap 写入失败，回退到数据库: taskId={}, partNumber={}", 
                part.getTaskId(), part.getPartNumber(), e);
            savePartToDatabase(part);
        }
    }
    
    @Override
    public int countCompletedParts(String taskId) {
        if (!bitmapProperties.isEnabled()) {
            return uploadPartMapper.countByTaskId(taskId);
        }
        
        try {
            String bitmapKey = getBitmapKey(taskId);
            Long count = redisTemplate.execute((RedisCallback<Long>) connection -> 
                connection.bitCount(bitmapKey.getBytes())
            );
            
            if (count != null) {
                return count.intValue();
            }
        } catch (Exception e) {
            log.warn("Bitmap 查询失败，回退到数据库: taskId={}", taskId, e);
        }
        
        return uploadPartMapper.countByTaskId(taskId);
    }
    
    @Override
    public List<Integer> findCompletedPartNumbers(String taskId) {
        if (!bitmapProperties.isEnabled()) {
            return uploadPartMapper.findPartNumbersByTaskId(taskId);
        }
        
        try {
            String bitmapKey = getBitmapKey(taskId);
            List<Integer> completedParts = new ArrayList<>();
            
            // 获取总分片数
            Long bitCount = redisTemplate.execute((RedisCallback<Long>) connection -> 
                connection.bitCount(bitmapKey.getBytes())
            );
            
            if (bitCount != null && bitCount > 0) {
                // 遍历 Bitmap
                for (int i = 0; i < bitmapProperties.getMaxParts(); i++) {
                    Boolean bit = redisTemplate.opsForValue().getBit(bitmapKey, i);
                    if (Boolean.TRUE.equals(bit)) {
                        completedParts.add(i + 1);
                    }
                    
                    if (completedParts.size() >= bitCount) {
                        break;
                    }
                }
                return completedParts;
            }
        } catch (Exception e) {
            log.warn("Bitmap 查询失败，回退到数据库: taskId={}", taskId, e);
        }
        
        return uploadPartMapper.findPartNumbersByTaskId(taskId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAllPartsToDatabase(String taskId, List<UploadPart> parts) {
        try {
            if (!parts.isEmpty()) {
                uploadPartMapper.batchInsert(parts);
                log.info("全量同步分片记录: taskId={}, count={}", taskId, parts.size());
            }
            
            // 删除 Bitmap 释放内存
            if (bitmapProperties.isEnabled()) {
                String bitmapKey = getBitmapKey(taskId);
                redisTemplate.delete(bitmapKey);
            }
        } catch (Exception e) {
            log.error("全量同步失败: taskId={}", taskId, e);
            throw new RuntimeException("同步分片记录失败", e);
        }
    }
    
    @Override
    public void loadPartsFromDatabase(String taskId) {
        if (!bitmapProperties.isEnabled()) {
            return;
        }
        
        try {
            List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(taskId);
            if (partNumbers.isEmpty()) {
                return;
            }
            
            String bitmapKey = getBitmapKey(taskId);
            for (Integer partNumber : partNumbers) {
                redisTemplate.opsForValue().setBit(bitmapKey, partNumber - 1, true);
            }
            
            redisTemplate.expire(bitmapKey, 
                Duration.ofHours(bitmapProperties.getExpireHours()));
            
            log.info("从数据库加载分片状态: taskId={}, count={}", taskId, partNumbers.size());
        } catch (Exception e) {
            log.error("加载分片状态失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 判断是否需要同步
     */
    private boolean shouldSync(int partNumber) {
        return partNumber % bitmapProperties.getSyncBatchSize() == 0;
    }
    
    /**
     * 异步同步到数据库
     */
    @Async
    private void asyncSyncToDatabase(String taskId) {
        try {
            List<Integer> completedParts = findCompletedPartNumbers(taskId);
            List<Integer> existingParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
            
            List<Integer> newParts = completedParts.stream()
                .filter(p -> !existingParts.contains(p))
                .collect(Collectors.toList());
            
            if (!newParts.isEmpty()) {
                List<UploadPart> partsToInsert = newParts.stream()
                    .map(partNumber -> UploadPart.builder()
                        .taskId(taskId)
                        .partNumber(partNumber)
                        .uploadedAt(LocalDateTime.now())
                        .build())
                    .collect(Collectors.toList());
                
                uploadPartMapper.batchInsert(partsToInsert);
                log.debug("异步同步分片: taskId={}, count={}", taskId, newParts.size());
            }
        } catch (Exception e) {
            log.error("异步同步失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 直接写入数据库
     */
    private void savePartToDatabase(UploadPart part) {
        uploadPartMapper.insert(part);
    }
    
    /**
     * 获取 Bitmap Key
     */
    private String getBitmapKey(String taskId) {
        return BITMAP_KEY_PREFIX + taskId + BITMAP_KEY_SUFFIX;
    }
}
```

### 3. BitmapProperties 配置类

```java
/**
 * Bitmap 优化配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.multipart.bitmap")
public class BitmapProperties {
    
    /**
     * 是否启用 Bitmap 优化
     */
    private boolean enabled = true;
    
    /**
     * 同步批次大小（每 N 个分片同步一次）
     */
    private int syncBatchSize = 10;
    
    /**
     * Bitmap 过期时间（小时）
     */
    private int expireHours = 24;
    
    /**
     * 最大分片数（用于遍历 Bitmap）
     */
    private int maxParts = 10000;
}
```

### 4. UploadPartMapper 数据访问层

```java
/**
 * 分片上传 Mapper 接口
 */
@Mapper
public interface UploadPartMapper {
    
    /**
     * 插入分片记录
     */
    void insert(UploadPart part);
    
    /**
     * 插入分片记录（如果已存在则忽略）
     */
    @Insert("INSERT INTO upload_parts (id, task_id, part_number, etag, size, uploaded_at) " +
            "VALUES (#{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt}) " +
            "ON CONFLICT (task_id, part_number) DO NOTHING")
    void insertOrIgnore(UploadPart part);
    
    /**
     * 批量插入分片记录
     */
    void batchInsert(@Param("parts") List<UploadPart> parts);
    
    /**
     * 统计任务的分片数量
     */
    @Select("SELECT COUNT(*) FROM upload_parts WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") String taskId);
    
    /**
     * 查询任务的所有分片编号
     */
    @Select("SELECT part_number FROM upload_parts WHERE task_id = #{taskId} ORDER BY part_number")
    List<Integer> findPartNumbersByTaskId(@Param("taskId") String taskId);
    
    /**
     * 查询任务的所有分片
     */
    @Select("SELECT * FROM upload_parts WHERE task_id = #{taskId} ORDER BY part_number")
    List<UploadPart> findByTaskId(@Param("taskId") String taskId);
    
    /**
     * 删除任务的所有分片
     */
    @Delete("DELETE FROM upload_parts WHERE task_id = #{taskId}")
    void deleteByTaskId(@Param("taskId") String taskId);
}
```

```xml
<!-- UploadPartMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper">
    
    <!-- 批量插入 -->
    <insert id="batchInsert">
        INSERT INTO upload_parts (id, task_id, part_number, etag, size, uploaded_at)
        VALUES
        <foreach collection="parts" item="part" separator=",">
            (#{part.id}, #{part.taskId}, #{part.partNumber}, 
             #{part.etag}, #{part.size}, #{part.uploadedAt})
        </foreach>
        ON CONFLICT (task_id, part_number) DO NOTHING
    </insert>
    
</mapper>
```

### 5. AsyncConfig 异步配置

```java
/**
 * 异步任务配置
 * 为 Bitmap 同步操作提供独立的线程池
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * Bitmap 同步专用线程池
     */
    @Bean(name = "bitmapSyncExecutor")
    public Executor bitmapSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        
        // 最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量
        executor.setQueueCapacity(100);
        
        // 线程名称前缀
        executor.setThreadNamePrefix("bitmap-sync-");
        
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);
        
        // 等待任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
```

### 6. Redis Key 设计

```java
/**
 * Redis Key 常量
 */
public class UploadRedisKeys {
    
    private static final String PREFIX = "upload:task:";
    
    /**
     * 分片状态 Bitmap
     * Key: upload:task:{taskId}:parts
     * Type: Bitmap
     * TTL: 24 小时
     * 
     * Bit 位置 = partNumber - 1
     * Bit 值: 0=未上传, 1=已上传
     */
    public static String partsBitmap(String taskId) {
        return PREFIX + taskId + ":parts";
    }
}
```

## Data Models

### 1. UploadPart 领域模型

```java
/**
 * 上传分片领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPart {
    
    /**
     * 分片ID (UUIDv7)
     */
    private String id;
    
    /**
     * 上传任务ID
     */
    private String taskId;
    
    /**
     * 分片编号（从1开始）
     */
    private Integer partNumber;
    
    /**
     * S3 返回的ETag
     */
    private String etag;
    
    /**
     * 分片大小（字节）
     */
    private Long size;
    
    /**
     * 上传时间
     */
    private LocalDateTime uploadedAt;
}
```

### 2. 数据库表结构

```sql
-- upload_parts 表（保持不变）
CREATE TABLE upload_parts (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    part_number INTEGER NOT NULL,
    etag VARCHAR(255),
    size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (task_id, part_number)
);

CREATE INDEX idx_upload_parts_task_id ON upload_parts(task_id);
```

### 3. Redis Bitmap 结构

```
Key: upload:task:{taskId}:parts
Type: Bitmap
TTL: 24 hours

示例：1000 个分片
- 存储空间: 125 字节 (1000 bits / 8)
- Bit 0 (partNumber 1): 1 = 已上传
- Bit 1 (partNumber 2): 0 = 未上传
- Bit 2 (partNumber 3): 1 = 已上传
- ...
- Bit 999 (partNumber 1000): 1 = 已上传

操作:
- SETBIT upload:task:abc123:parts 0 1  # 记录分片 1
- GETBIT upload:task:abc123:parts 0    # 查询分片 1
- BITCOUNT upload:task:abc123:parts    # 统计已上传分片数
```

## Correctness Properties

*属性（Property）是系统在所有有效执行中应该保持为真的特征或行为。属性是人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: Bitmap 记录正确性

*For any* 上传任务和分片编号，当记录分片状态时，Redis Bitmap 中对应的 bit 位置应该被设置为 1，且 key 格式为 `upload:task:{taskId}:parts`，bit 偏移量为 `partNumber - 1`

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Bitmap TTL 设置

*For any* 新创建的 Bitmap key，其过期时间应该在 23-24 小时之间（考虑执行延迟）

**Validates: Requirements 1.4**

### Property 3: Bitmap 查询一致性

*For any* 上传任务，从 Bitmap 查询的已完成分片列表应该与 Bitmap 中所有值为 1 的 bit 位置对应的分片编号一致

**Validates: Requirements 2.1, 2.2**

### Property 4: 上传进度计算正确性

*For any* 上传任务，上传进度应该等于已完成分片数量除以总分片数量

**Validates: Requirements 2.3**

### Property 5: 定期同步触发条件

*For any* 上传任务，当上传的分片编号是同步批次大小的倍数时，应该触发异步同步到数据库

**Validates: Requirements 3.1, 3.5**

### Property 6: 同步差异处理

*For any* 同步操作，应该只将 Bitmap 中存在但数据库中不存在的分片记录插入数据库

**Validates: Requirements 3.2, 3.3**

### Property 7: 同步失败不影响上传

*For any* 同步操作失败，不应该抛出异常导致上传流程中断

**Validates: Requirements 3.4**

### Property 8: 全量同步数据完整性

*For any* 完成的上传任务，数据库中的分片记录数量应该等于总分片数量，且每条记录包含 partNumber、etag 和 uploadedAt

**Validates: Requirements 4.1, 4.5**

### Property 9: 全量同步使用批量插入

*For any* 全量同步操作，应该使用批量插入而不是逐条插入

**Validates: Requirements 4.2**

### Property 10: 同步成功后清理 Bitmap

*For any* 全量同步成功的任务，对应的 Bitmap key 应该被删除

**Validates: Requirements 4.3**

### Property 11: 同步失败保留 Bitmap

*For any* 全量同步失败的任务，应该抛出异常且对应的 Bitmap key 应该仍然存在

**Validates: Requirements 4.4**

### Property 12: Redis 故障自动回退

*For any* Redis 操作失败的情况，系统应该自动切换到数据库模式并成功完成操作

**Validates: Requirements 5.1, 5.2, 5.5**

### Property 13: Redis 恢复自动切换

*For any* Redis 从不可用恢复到可用的情况，下一次操作应该自动使用 Bitmap 模式

**Validates: Requirements 5.3**

### Property 14: 回退记录警告日志

*For any* 发生 Redis 故障回退的情况，应该记录 WARN 级别的日志且包含异常信息

**Validates: Requirements 5.4**

### Property 15: 配置禁用 Bitmap

*For any* Bitmap 功能被配置为禁用的情况，所有操作应该直接使用数据库而不尝试访问 Redis

**Validates: Requirements 6.4**

### Property 16: 操作日志记录

*For any* 分片记录操作，应该记录 DEBUG 级别日志包含 taskId 和 partNumber

**Validates: Requirements 7.1**

### Property 17: 错误日志记录

*For any* Redis 操作失败，应该记录 WARN 级别日志且包含异常堆栈信息

**Validates: Requirements 7.2**

### Property 18: 同步日志记录

*For any* 同步操作，应该记录 INFO 级别日志且包含同步的分片数量

**Validates: Requirements 7.3**

### Property 19: 查询回退到数据库

*For any* Bitmap 不存在的上传任务，查询分片状态应该自动从数据库获取

**Validates: Requirements 8.2**

### Property 20: 断点续传状态恢复

*For any* 从数据库加载分片状态的操作，Bitmap 中应该包含数据库中所有已完成分片的记录

**Validates: Requirements 8.3**

### Property 21: 完成任务数据一致性

*For all* 状态为已完成的上传任务，数据库中应该有完整的分片记录且数量等于总分片数

**Validates: Requirements 8.1, 8.5**

### Property 22: 数据库写入减少

*For any* 使用 Bitmap 优化的上传任务，数据库写入次数应该不超过总分片数的 20%（减少至少 80%）

**Validates: Requirements 9.3**

## Error Handling

### 1. Redis 连接失败

**场景**: Redis 服务不可用或网络故障

**处理策略**:
```java
try {
    // 尝试 Redis 操作
    redisTemplate.opsForValue().setBit(key, offset, true);
} catch (RedisConnectionFailureException e) {
    // 记录警告日志
    log.warn("Redis 连接失败，回退到数据库: taskId={}", taskId, e);
    // 自动回退到数据库
    savePartToDatabase(part);
}
```

**影响**: 性能降低但功能正常

### 2. Redis 操作超时

**场景**: Redis 响应缓慢导致超时

**处理策略**:
```java
try {
    redisTemplate.opsForValue().setBit(key, offset, true);
} catch (QueryTimeoutException e) {
    log.warn("Redis 操作超时，回退到数据库: taskId={}", taskId, e);
    savePartToDatabase(part);
}
```

**影响**: 单次请求延迟增加，自动回退

### 3. 数据库同步失败

**场景**: 异步同步到数据库时发生错误

**处理策略**:
```java
@Async
private void asyncSyncToDatabase(String taskId) {
    try {
        // 同步逻辑
        uploadPartMapper.batchInsert(parts);
    } catch (Exception e) {
        // 记录错误但不抛出异常
        log.error("异步同步失败: taskId={}", taskId, e);
        // 不影响上传流程继续
    }
}
```

**影响**: 数据暂时不一致，完成时全量同步会修复

### 4. 全量同步失败

**场景**: 上传完成时同步到数据库失败

**处理策略**:
```java
@Transactional(rollbackFor = Exception.class)
public void syncAllPartsToDatabase(String taskId, List<UploadPart> parts) {
    try {
        uploadPartMapper.batchInsert(parts);
        redisTemplate.delete(bitmapKey);
    } catch (Exception e) {
        log.error("全量同步失败: taskId={}", taskId, e);
        // 抛出异常，保留 Bitmap，允许重试
        throw new RuntimeException("同步分片记录失败", e);
    }
}
```

**影响**: 上传完成失败，客户端需要重试

### 5. Bitmap 数据丢失

**场景**: Redis 重启或数据被驱逐

**处理策略**:
```java
public List<Integer> findCompletedPartNumbers(String taskId) {
    try {
        // 尝试从 Bitmap 查询
        List<Integer> parts = queryFromBitmap(taskId);
        if (parts.isEmpty()) {
            // Bitmap 不存在，从数据库加载
            parts = uploadPartMapper.findPartNumbersByTaskId(taskId);
        }
        return parts;
    } catch (Exception e) {
        // 回退到数据库
        return uploadPartMapper.findPartNumbersByTaskId(taskId);
    }
}
```

**影响**: 性能降低但数据不丢失

### 6. 配置错误

**场景**: 配置的批次大小或过期时间不合理

**处理策略**:
```java
@PostConstruct
public void validateConfig() {
    if (bitmapProperties.getSyncBatchSize() <= 0) {
        log.warn("同步批次大小配置无效，使用默认值 10");
        bitmapProperties.setSyncBatchSize(10);
    }
    if (bitmapProperties.getExpireHours() <= 0) {
        log.warn("过期时间配置无效，使用默认值 24 小时");
        bitmapProperties.setExpireHours(24);
    }
}
```

**影响**: 使用默认值，系统正常运行

## Testing Strategy

### 测试方法

本功能采用**双重测试策略**：
- **单元测试**: 验证具体示例、边界条件和错误处理
- **属性测试**: 验证通用属性在所有输入下都成立

两种测试方法互补，共同保证系统正确性。

### 单元测试

#### 1. Bitmap 操作测试

```java
@Test
void testSavePartToBitmap() {
    // Given
    UploadPart part = UploadPart.builder()
        .taskId("task123")
        .partNumber(5)
        .build();
    
    // When
    repository.savePart(part);
    
    // Then
    String key = "upload:task:task123:parts";
    Boolean bit = redisTemplate.opsForValue().getBit(key, 4);
    assertThat(bit).isTrue();
}

@Test
void testCountCompletedParts() {
    // Given
    String taskId = "task123";
    setBits(taskId, 1, 3, 5, 7);
    
    // When
    int count = repository.countCompletedParts(taskId);
    
    // Then
    assertThat(count).isEqualTo(4);
}
```

#### 2. 故障回退测试

```java
@Test
void testRedisFailureFallbackToDatabase() {
    // Given
    when(redisTemplate.opsForValue()).thenThrow(RedisConnectionFailureException.class);
    UploadPart part = createTestPart();
    
    // When
    repository.savePart(part);
    
    // Then
    verify(uploadPartMapper).insert(part);
}
```

#### 3. 同步逻辑测试

```java
@Test
void testAsyncSyncOnlyNewParts() {
    // Given
    String taskId = "task123";
    setBitsInRedis(taskId, 1, 2, 3, 4, 5);
    setPartsInDatabase(taskId, 1, 2, 3);
    
    // When
    repository.asyncSyncToDatabase(taskId);
    
    // Then
    ArgumentCaptor<List<UploadPart>> captor = ArgumentCaptor.forClass(List.class);
    verify(uploadPartMapper).batchInsert(captor.capture());
    List<UploadPart> inserted = captor.getValue();
    assertThat(inserted).hasSize(2);
    assertThat(inserted).extracting("partNumber").containsExactly(4, 5);
}
```

### 属性测试

使用 **JUnit QuickCheck** 进行属性测试，每个测试运行 100 次随机输入。

#### 1. Bitmap 记录正确性

```java
@Property(trials = 100)
void bitmapRecordCorrectness(
    @ForAll @StringLength(min = 10, max = 36) String taskId,
    @ForAll @IntRange(min = 1, max = 1000) int partNumber) {
    
    // Feature: multipart-upload-bitmap-optimization, Property 1
    
    // Given
    UploadPart part = UploadPart.builder()
        .taskId(taskId)
        .partNumber(partNumber)
        .build();
    
    // When
    repository.savePart(part);
    
    // Then
    String expectedKey = "upload:task:" + taskId + ":parts";
    int expectedOffset = partNumber - 1;
    Boolean bit = redisTemplate.opsForValue().getBit(expectedKey, expectedOffset);
    
    assertThat(bit).isTrue();
}
```

#### 2. 查询一致性

```java
@Property(trials = 100)
void queryConsistency(
    @ForAll @StringLength(min = 10, max = 36) String taskId,
    @ForAll @Size(min = 1, max = 100) List<@IntRange(min = 1, max = 1000) Integer> partNumbers) {
    
    // Feature: multipart-upload-bitmap-optimization, Property 3
    
    // Given - 设置随机的分片状态
    for (Integer partNumber : partNumbers) {
        setBit(taskId, partNumber);
    }
    
    // When
    List<Integer> queriedParts = repository.findCompletedPartNumbers(taskId);
    
    // Then
    assertThat(queriedParts).containsExactlyInAnyOrderElementsOf(partNumbers);
}
```

#### 3. 同步触发条件

```java
@Property(trials = 100)
void syncTriggerCondition(
    @ForAll @StringLength(min = 10, max = 36) String taskId,
    @ForAll @IntRange(min = 1, max = 100) int batchSize,
    @ForAll @IntRange(min = 1, max = 1000) int partNumber) {
    
    // Feature: multipart-upload-bitmap-optimization, Property 5
    
    // Given
    bitmapProperties.setSyncBatchSize(batchSize);
    UploadPart part = createPart(taskId, partNumber);
    
    // When
    repository.savePart(part);
    
    // Then
    boolean shouldSync = (partNumber % batchSize == 0);
    if (shouldSync) {
        verify(asyncExecutor, timeout(1000)).execute(any(Runnable.class));
    } else {
        verify(asyncExecutor, never()).execute(any(Runnable.class));
    }
}
```

#### 4. 故障回退

```java
@Property(trials = 100)
void redisFailureAutoFallback(
    @ForAll @StringLength(min = 10, max = 36) String taskId,
    @ForAll @IntRange(min = 1, max = 1000) int partNumber) {
    
    // Feature: multipart-upload-bitmap-optimization, Property 12
    
    // Given - Mock Redis 失败
    when(redisTemplate.opsForValue()).thenThrow(RedisConnectionFailureException.class);
    UploadPart part = createPart(taskId, partNumber);
    
    // When
    assertDoesNotThrow(() -> repository.savePart(part));
    
    // Then - 应该回退到数据库
    verify(uploadPartMapper).insert(part);
}
```

#### 5. 数据一致性

```java
@Property(trials = 100)
void completedTaskDataConsistency(
    @ForAll @StringLength(min = 10, max = 36) String taskId,
    @ForAll @IntRange(min = 10, max = 100) int totalParts) {
    
    // Feature: multipart-upload-bitmap-optimization, Property 21
    
    // Given - 上传所有分片
    List<UploadPart> parts = new ArrayList<>();
    for (int i = 1; i <= totalParts; i++) {
        UploadPart part = createPart(taskId, i);
        repository.savePart(part);
        parts.add(part);
    }
    
    // When - 完成上传
    repository.syncAllPartsToDatabase(taskId, parts);
    
    // Then - 数据库应该有所有分片记录
    int dbCount = uploadPartMapper.countByTaskId(taskId);
    assertThat(dbCount).isEqualTo(totalParts);
    
    List<UploadPart> dbParts = uploadPartMapper.findByTaskId(taskId);
    assertThat(dbParts).hasSize(totalParts);
    assertThat(dbParts).allMatch(p -> p.getPartNumber() != null);
    assertThat(dbParts).allMatch(p -> p.getUploadedAt() != null);
}
```

### 集成测试

```java
@SpringBootTest
@Testcontainers
class FileCacheBitmapIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @Test
    void testCompleteUploadWorkflow() {
        // Given
        String taskId = createUploadTask();
        int totalParts = 100;
        
        // When - 上传所有分片
        for (int i = 1; i <= totalParts; i++) {
            uploadPart(taskId, i);
        }
        
        // Then - 验证 Bitmap
        int bitmapCount = countFromBitmap(taskId);
        assertThat(bitmapCount).isEqualTo(totalParts);
        
        // When - 完成上传
        completeUpload(taskId);
        
        // Then - 验证数据库
        int dbCount = countFromDatabase(taskId);
        assertThat(dbCount).isEqualTo(totalParts);
        
        // Then - 验证 Bitmap 已删除
        boolean bitmapExists = redisTemplate.hasKey("upload:task:" + taskId + ":parts");
        assertThat(bitmapExists).isFalse();
    }
    
    @Test
    void testRedisFailureScenario() {
        // Given
        String taskId = createUploadTask();
        
        // When - 停止 Redis
        redis.stop();
        
        // Then - 上传仍然成功（回退到数据库）
        assertDoesNotThrow(() -> uploadPart(taskId, 1));
        
        // Then - 数据库有记录
        int dbCount = countFromDatabase(taskId);
        assertThat(dbCount).isEqualTo(1);
    }
}
```

### 测试配置

```yaml
# application-test.yml
storage:
  multipart:
    bitmap:
      enabled: true
      sync-batch-size: 5
      expire-hours: 1
      max-parts: 1000

spring:
  data:
    redis:
      host: localhost
      port: 6379
  datasource:
    url: jdbc:postgresql://localhost:5434/file_service_test
```

### 测试覆盖率目标

- 单元测试覆盖率: > 80%
- 属性测试: 每个属性 100 次随机输入
- 集成测试: 覆盖主要业务流程和故障场景
- 边界测试: 1 个分片、1000 个分片、10000 个分片

### 性能测试

```java
@Test
void performanceComparison() {
    // 测试 1000 个分片的性能
    String taskId = "perf-test";
    
    // Bitmap 模式
    long bitmapStart = System.currentTimeMillis();
    for (int i = 1; i <= 1000; i++) {
        repository.savePart(createPart(taskId, i));
    }
    long bitmapTime = System.currentTimeMillis() - bitmapStart;
    
    // 数据库模式
    bitmapProperties.setEnabled(false);
    String taskId2 = "perf-test-2";
    long dbStart = System.currentTimeMillis();
    for (int i = 1; i <= 1000; i++) {
        repository.savePart(createPart(taskId2, i));
    }
    long dbTime = System.currentTimeMillis() - dbStart;
    
    // 验证性能提升
    log.info("Bitmap 模式: {}ms, 数据库模式: {}ms, 提升: {}倍", 
        bitmapTime, dbTime, (double)dbTime / bitmapTime);
    
    assertThat(bitmapTime).isLessThan(dbTime / 5); // 至少 5 倍提升
}
```
## Edge Cases and Boundary Conditions

### 1. 用户上传后离开（任务未完成）

**场景**: 用户上传了部分分片后关闭浏览器或应用

**问题**:
- Bitmap 数据在 Redis 中占用内存
- 未完成的任务需要清理

**解决方案**:

#### 1.1 Bitmap 自动过期
```java
// 设置 24 小时 TTL
redisTemplate.expire(bitmapKey, Duration.ofHours(24));
```
- 24 小时后 Bitmap 自动删除
- 不会永久占用 Redis 内存

#### 1.2 定时清理任务
```java
@Scheduled(cron = "0 0 * * * *")  // 每小时执行
public void cleanupExpiredTasks() {
    LocalDateTime expireTime = LocalDateTime.now().minusHours(24);
    List<UploadTask> expiredTasks = uploadTaskRepository.findExpiredTasks(expireTime);
    
    for (UploadTask task : expiredTasks) {
        // 1. 删除 Bitmap
        String bitmapKey = getBitmapKey(task.getId());
        redisTemplate.delete(bitmapKey);
        
        // 2. 更新任务状态为 EXPIRED
        uploadTaskRepository.updateStatus(task.getId(), UploadTaskStatus.EXPIRED);
        
        // 3. 调用 S3 abortMultipartUpload
        s3StorageService.abortMultipartUpload(task.getStoragePath(), task.getUploadId());
        
        log.info("清理过期任务: taskId={}, userId={}", task.getId(), task.getUserId());
    }
}
```

#### 1.3 断点续传支持
```java
public ResumeUploadResponse resumeUpload(String taskId) {
    // 1. 查询任务
    UploadTask task = uploadTaskRepository.findById(taskId)
        .orElseThrow(() -> new TaskNotFoundException(taskId));
    
    // 2. 检查是否过期
    if (task.isExpired()) {
        throw new TaskExpiredException("任务已过期");
    }
    
    // 3. 从数据库加载已完成的分片到 Bitmap
    repository.loadPartsFromDatabase(taskId);
    
    // 4. 返回已完成的分片列表
    List<Integer> completedParts = repository.findCompletedPartNumbers(taskId);
    
    return ResumeUploadResponse.builder()
        .taskId(taskId)
        .totalParts(task.getTotalParts())
        .completedParts(completedParts)
        .uploadId(task.getUploadId())
        .build();
}
```

### 2. 用户上传过程中断网

**场景**: 网络中断导致部分分片上传失败

**问题**:
- 客户端不知道哪些分片已成功
- 可能重复上传已成功的分片

**解决方案**:

#### 2.1 幂等性设计
```java
@Override
public void savePart(UploadPart part) {
    String bitmapKey = getBitmapKey(part.getTaskId());
    int bitOffset = part.getPartNumber() - 1;
    
    // SETBIT 是幂等操作，重复设置不会有副作用
    redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
    
    // 数据库也使用 UPSERT（ON CONFLICT DO NOTHING）
    uploadPartMapper.insertOrIgnore(part);
}
```

```sql
-- MyBatis Mapper
INSERT INTO upload_parts (id, task_id, part_number, etag, size, uploaded_at)
VALUES (#{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt})
ON CONFLICT (task_id, part_number) DO NOTHING;
```

#### 2.2 客户端重试机制
```java
// 客户端伪代码
public void uploadWithRetry(int partNumber, byte[] data) {
    int maxRetries = 3;
    for (int i = 0; i < maxRetries; i++) {
        try {
            // 1. 上传分片到 S3
            String etag = s3Client.uploadPart(uploadId, partNumber, data);
            
            // 2. 记录分片状态
            fileService.recordPart(taskId, partNumber, etag);
            
            return; // 成功
        } catch (NetworkException e) {
            if (i == maxRetries - 1) {
                throw e; // 最后一次重试失败
            }
            Thread.sleep(1000 * (i + 1)); // 指数退避
        }
    }
}
```

#### 2.3 查询已完成分片
```java
@GetMapping("/upload/{taskId}/parts")
public List<Integer> getCompletedParts(@PathVariable String taskId) {
    // 客户端可以随时查询已完成的分片
    return repository.findCompletedPartNumbers(taskId);
}
```

### 3. Redis 宕机期间的数据一致性

**场景**: Redis 宕机，部分分片记录只在 Bitmap 中

**问题**:
- Bitmap 数据丢失
- 数据库中的记录不完整

**解决方案**:

#### 3.1 定期同步保证数据持久化
```java
// 每 10 个分片同步一次
private static final int SYNC_BATCH_SIZE = 10;

@Override
public void savePart(UploadPart part) {
    try {
        // 1. 写入 Bitmap
        setBit(part.getTaskId(), part.getPartNumber());
        
        // 2. 定期同步
        if (part.getPartNumber() % SYNC_BATCH_SIZE == 0) {
            asyncSyncToDatabase(part.getTaskId());
        }
    } catch (RedisException e) {
        // 3. Redis 失败立即写数据库
        savePartToDatabase(part);
    }
}
```

#### 3.2 完成时全量同步
```java
@Override
public void completeUpload(String taskId) {
    // 1. 从 Bitmap 获取所有分片
    List<Integer> bitmapParts = findCompletedPartNumbers(taskId);
    
    // 2. 从数据库获取已有分片
    List<Integer> dbParts = uploadPartMapper.findPartNumbersByTaskId(taskId);
    
    // 3. 找出差异
    List<Integer> missingParts = bitmapParts.stream()
        .filter(p -> !dbParts.contains(p))
        .collect(Collectors.toList());
    
    // 4. 补充缺失的分片记录
    if (!missingParts.isEmpty()) {
        List<UploadPart> partsToInsert = missingParts.stream()
            .map(partNumber -> UploadPart.builder()
                .taskId(taskId)
                .partNumber(partNumber)
                .uploadedAt(LocalDateTime.now())
                .build())
            .collect(Collectors.toList());
        
        uploadPartMapper.batchInsert(partsToInsert);
        log.info("补充缺失的分片记录: taskId={}, count={}", taskId, missingParts.size());
    }
    
    // 5. 删除 Bitmap
    redisTemplate.delete(getBitmapKey(taskId));
}
```

#### 3.3 Redis 恢复后的状态重建
```java
@Override
public void loadPartsFromDatabase(String taskId) {
    try {
        // 从数据库加载分片状态
        List<Integer> partNumbers = uploadPartMapper.findPartNumbersByTaskId(taskId);
        
        if (partNumbers.isEmpty()) {
            return;
        }
        
        // 重建 Bitmap
        String bitmapKey = getBitmapKey(taskId);
        for (Integer partNumber : partNumbers) {
            redisTemplate.opsForValue().setBit(bitmapKey, partNumber - 1, true);
        }
        
        redisTemplate.expire(bitmapKey, Duration.ofHours(24));
        
        log.info("从数据库重建 Bitmap: taskId={}, parts={}", taskId, partNumbers.size());
    } catch (Exception e) {
        log.error("重建 Bitmap 失败: taskId={}", taskId, e);
    }
}
```

### 4. 并发上传同一任务

**场景**: 客户端多线程并发上传同一文件的不同分片

**问题**:
- Bitmap 并发写入
- 数据库并发插入

**解决方案**:

#### 4.1 Redis Bitmap 天然支持并发
```java
// SETBIT 是原子操作，天然支持并发
redisTemplate.opsForValue().setBit(key, offset, true);
```

#### 4.2 数据库使用唯一约束
```sql
CREATE TABLE upload_parts (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    part_number INTEGER NOT NULL,
    etag VARCHAR(255),
    size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (task_id, part_number)  -- 防止重复插入
);
```

#### 4.3 使用 UPSERT 避免冲突
```sql
INSERT INTO upload_parts (id, task_id, part_number, etag, size, uploaded_at)
VALUES (#{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt})
ON CONFLICT (task_id, part_number) 
DO UPDATE SET 
    etag = EXCLUDED.etag,
    size = EXCLUDED.size,
    uploaded_at = EXCLUDED.uploaded_at;
```

### 5. 极大文件（10000+ 分片）

**场景**: 上传超大文件，分片数量超过 10000

**问题**:
- Bitmap 遍历性能
- 内存占用

**解决方案**:

#### 5.1 Bitmap 大小限制
```java
@Data
@ConfigurationProperties(prefix = "storage.multipart.bitmap")
public class BitmapProperties {
    /**
     * 最大分片数
     */
    private int maxParts = 10000;
}
```

#### 5.2 超过限制时禁用 Bitmap
```java
@Override
public void savePart(UploadPart part) {
    UploadTask task = uploadTaskRepository.findById(part.getTaskId())
        .orElseThrow();
    
    // 超过最大分片数，直接使用数据库
    if (task.getTotalParts() > bitmapProperties.getMaxParts()) {
        log.info("分片数超过限制，使用数据库模式: taskId={}, totalParts={}", 
            part.getTaskId(), task.getTotalParts());
        savePartToDatabase(part);
        return;
    }
    
    // 正常使用 Bitmap
    saveToBitmap(part);
}
```

#### 5.3 优化 Bitmap 遍历
```java
@Override
public List<Integer> findCompletedPartNumbers(String taskId) {
    String bitmapKey = getBitmapKey(taskId);
    List<Integer> completedParts = new ArrayList<>();
    
    // 获取总数，避免无效遍历
    Long bitCount = redisTemplate.execute((RedisCallback<Long>) connection -> 
        connection.bitCount(bitmapKey.getBytes())
    );
    
    if (bitCount == null || bitCount == 0) {
        return completedParts;
    }
    
    // 只遍历到找到所有分片为止
    int maxParts = bitmapProperties.getMaxParts();
    for (int i = 0; i < maxParts && completedParts.size() < bitCount; i++) {
        Boolean bit = redisTemplate.opsForValue().getBit(bitmapKey, i);
        if (Boolean.TRUE.equals(bit)) {
            completedParts.add(i + 1);
        }
    }
    
    return completedParts;
}
```

### 6. 系统重启

**场景**: 应用服务器重启，内存中的状态丢失

**问题**:
- 正在进行的上传任务状态
- Bitmap 数据在 Redis 中但应用不知道

**解决方案**:

#### 6.1 无状态设计
```java
// 所有状态都存储在 Redis 和数据库中
// 应用重启不影响上传任务
```

#### 6.2 客户端主动查询
```java
// 客户端在重新连接后查询任务状态
@GetMapping("/upload/{taskId}/status")
public UploadStatusResponse getUploadStatus(@PathVariable String taskId) {
    UploadTask task = uploadTaskRepository.findById(taskId)
        .orElseThrow(() -> new TaskNotFoundException(taskId));
    
    // 从 Bitmap 或数据库查询已完成分片
    List<Integer> completedParts = repository.findCompletedPartNumbers(taskId);
    
    return UploadStatusResponse.builder()
        .taskId(taskId)
        .status(task.getStatus())
        .totalParts(task.getTotalParts())
        .completedParts(completedParts)
        .progress((double) completedParts.size() / task.getTotalParts())
        .build();
}
```

#### 6.3 断点续传
```java
// 客户端可以随时恢复上传
public void resumeUpload(String taskId) {
    // 1. 查询已完成的分片
    List<Integer> completedParts = getCompletedParts(taskId);
    
    // 2. 计算未完成的分片
    Set<Integer> completedSet = new HashSet<>(completedParts);
    List<Integer> pendingParts = new ArrayList<>();
    for (int i = 1; i <= totalParts; i++) {
        if (!completedSet.contains(i)) {
            pendingParts.add(i);
        }
    }
    
    // 3. 继续上传未完成的分片
    for (Integer partNumber : pendingParts) {
        uploadPart(taskId, partNumber);
    }
}
```

### 7. 分片编号错误

**场景**: 客户端发送错误的分片编号（<1 或 >totalParts）

**问题**:
- Bitmap 位偏移量越界
- 数据不一致

**解决方案**:

#### 7.1 参数校验
```java
@Override
public void savePart(UploadPart part) {
    // 1. 查询任务
    UploadTask task = uploadTaskRepository.findById(part.getTaskId())
        .orElseThrow(() -> new TaskNotFoundException(part.getTaskId()));
    
    // 2. 校验分片编号
    if (part.getPartNumber() < 1 || part.getPartNumber() > task.getTotalParts()) {
        throw new IllegalArgumentException(
            String.format("分片编号无效: partNumber=%d, totalParts=%d", 
                part.getPartNumber(), task.getTotalParts())
        );
    }
    
    // 3. 记录分片
    saveToBitmap(part);
}
```

#### 7.2 Controller 层校验
```java
@PostMapping("/upload/{taskId}/parts/{partNumber}")
public Result<Void> uploadPart(
    @PathVariable String taskId,
    @PathVariable @Min(1) @Max(10000) Integer partNumber,
    @RequestParam("file") MultipartFile file) {
    
    // 参数校验由 @Valid 自动完成
    multipartUploadService.uploadPart(taskId, partNumber, file);
    return Result.success();
}
```

### 8. 数据库连接池耗尽

**场景**: 大量并发上传导致数据库连接池耗尽

**问题**:
- 同步操作无法获取数据库连接
- 上传失败

**解决方案**:

#### 8.1 使用 Bitmap 减少数据库压力
```java
// Bitmap 模式下，数据库写入减少 90%
// 1000 个分片只需要 ~100 次数据库写入
```

#### 8.2 异步同步使用独立线程池
```java
@Configuration
public class AsyncConfig {
    
    @Bean(name = "syncExecutor")
    public Executor syncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Async("syncExecutor")
private void asyncSyncToDatabase(String taskId) {
    // 使用独立线程池，不影响主业务
}
```

#### 8.3 数据库连接池配置
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 9. Bitmap Key 冲突

**场景**: 不同租户使用相同的 taskId

**问题**:
- Bitmap 数据混淆
- 数据泄露

**解决方案**:

#### 9.1 Key 包含租户信息
```java
public class UploadRedisKeys {
    
    /**
     * 分片状态 Bitmap
     * Key: upload:task:{appId}:{taskId}:parts
     */
    public static String partsBitmap(String appId, String taskId) {
        return "upload:task:" + appId + ":" + taskId + ":parts";
    }
}
```

#### 9.2 使用 UUIDv7 作为 taskId
```java
// UUIDv7 包含时间戳，全局唯一
String taskId = UUIDv7.generate();
```

### 10. 内存溢出

**场景**: 大量并发上传任务占用过多内存

**问题**:
- Redis 内存不足
- 应用 OOM

**解决方案**:

#### 10.1 Bitmap 自动过期
```java
// 24 小时后自动删除
redisTemplate.expire(bitmapKey, Duration.ofHours(24));
```

#### 10.2 Redis 内存淘汰策略
```yaml
# redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
```

#### 10.3 监控和告警
```java
@Scheduled(fixedRate = 60000)
public void monitorRedisMemory() {
    Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> 
        connection.info("memory")
    );
    
    long usedMemory = Long.parseLong(info.getProperty("used_memory"));
    long maxMemory = Long.parseLong(info.getProperty("maxmemory"));
    
    double usage = (double) usedMemory / maxMemory;
    if (usage > 0.8) {
        log.warn("Redis 内存使用率过高: {}%", usage * 100);
        // 发送告警
    }
}
```

## Edge Cases Summary

| 边界情况 | 影响 | 解决方案 | 数据丢失风险 |
|---------|------|---------|-------------|
| 用户离开 | 内存占用 | TTL + 定时清理 | 无 |
| 断网重连 | 重复上传 | 幂等设计 + 查询接口 | 无 |
| Redis 宕机 | 性能降低 | 自动回退 + 定期同步 | 低（最多丢失 10 个分片） |
| 并发上传 | 数据冲突 | 原子操作 + 唯一约束 | 无 |
| 超大文件 | 性能问题 | 限制 + 降级 | 无 |
| 系统重启 | 状态丢失 | 无状态设计 + 查询接口 | 无 |
| 参数错误 | 数据错误 | 参数校验 | 无 |
| 连接池耗尽 | 服务不可用 | Bitmap 减压 + 独立线程池 | 无 |
| Key 冲突 | 数据混淆 | 租户隔离 + UUIDv7 | 无 |
| 内存溢出 | 服务崩溃 | TTL + 淘汰策略 + 监控 | 无 |
## Monitoring and Observability

### 监控架构

```
┌─────────────────────────────────────────────────────────────┐
│                    File Service                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Spring Boot Actuator + Micrometer                    │  │
│  │  - 收集指标                                            │  │
│  │  - 暴露 /actuator/prometheus 端点                     │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP Pull (每 15 秒)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Prometheus                                │
│  - 时序数据库                                                │
│  - 数据采集和存储                                            │
│  - PromQL 查询                                               │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP Query
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Grafana                                   │
│  - 可视化仪表板                                              │
│  - 告警配置                                                  │
│  - 数据分析                                                  │
└─────────────────────────────────────────────────────────────┘
```

### 1. 依赖配置

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Boot Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Micrometer Prometheus Registry -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

### 2. 应用配置

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
    export:
      prometheus:
        enabled: true
```

### 3. 监控指标类

```java
/**
 * Bitmap 监控指标
 * 使用 Micrometer 记录各类指标
 */
@Component
@RequiredArgsConstructor
public class BitmapMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 缓存命中/未命中计数器
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    /**
     * 记录 Bitmap 写入成功
     */
    public void recordWriteSuccess() {
        meterRegistry.counter("bitmap.write",
            "result", "success"
        ).increment();
    }
    
    /**
     * 记录 Bitmap 写入失败
     */
    public void recordWriteFailure(String errorType) {
        meterRegistry.counter("bitmap.write",
            "result", "failure",
            "error", errorType
        ).increment();
    }
    
    /**
     * 记录回退到数据库
     */
    public void recordFallback(String reason) {
        meterRegistry.counter("bitmap.fallback",
            "reason", reason
        ).increment();
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
        meterRegistry.counter("bitmap.cache",
            "result", "hit"
        ).increment();
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
        meterRegistry.counter("bitmap.cache",
            "result", "miss"
        ).increment();
    }
    
    /**
     * 记录操作耗时
     */
    public <T> T recordTiming(String operation, Supplier<T> supplier) {
        return Timer.builder("bitmap.operation.duration")
            .tag("operation", operation)
            .register(meterRegistry)
            .record(supplier);
    }
    
    /**
     * 记录同步操作
     */
    public void recordSync(int partCount) {
        meterRegistry.counter("bitmap.sync.total").increment();
        meterRegistry.counter("bitmap.sync.parts").increment(partCount);
    }
    
    /**
     * 注册缓存命中率 Gauge
     */
    @PostConstruct
    public void registerCacheHitRatio() {
        Gauge.builder("bitmap.cache.hit.ratio", () -> {
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        })
        .description("Bitmap 缓存命中率")
        .register(meterRegistry);
    }
    
    /**
     * 注册活跃任务数 Gauge
     */
    public void registerActiveTasksGauge(Supplier<Integer> activeTasksSupplier) {
        Gauge.builder("bitmap.active.tasks", activeTasksSupplier, Supplier::get)
            .description("当前活跃的上传任务数")
            .register(meterRegistry);
    }
}
```

### 4. Repository 集成监控

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadPartRepositoryImpl implements UploadPartRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final UploadPartMapper uploadPartMapper;
    private final BitmapMetrics metrics;
    
    @Override
    public void savePart(UploadPart part) {
        metrics.recordTiming("savePart", () -> {
            try {
                // 写入 Bitmap
                String bitmapKey = getBitmapKey(part.getTaskId());
                int bitOffset = part.getPartNumber() - 1;
                
                redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
                redisTemplate.expire(bitmapKey, Duration.ofHours(24));
                
                // 记录成功
                metrics.recordWriteSuccess();
                
                log.debug("分片记录到 Bitmap: taskId={}, partNumber={}", 
                    part.getTaskId(), part.getPartNumber());
                
                return null;
            } catch (RedisConnectionFailureException e) {
                // 记录失败和回退
                metrics.recordWriteFailure("connection_failure");
                metrics.recordFallback("redis_unavailable");
                
                log.warn("Redis 连接失败，回退到数据库: taskId={}", 
                    part.getTaskId(), e);
                
                savePartToDatabase(part);
                return null;
            }
        });
    }
    
    @Override
    public int countCompletedParts(String taskId) {
        return metrics.recordTiming("countParts", () -> {
            try {
                String bitmapKey = getBitmapKey(taskId);
                Long count = redisTemplate.execute((RedisCallback<Long>) connection -> 
                    connection.bitCount(bitmapKey.getBytes())
                );
                
                if (count != null) {
                    metrics.recordCacheHit();
                    return count.intValue();
                }
            } catch (Exception e) {
                log.warn("Bitmap 查询失败，回退到数据库: taskId={}", taskId, e);
            }
            
            metrics.recordCacheMiss();
            return uploadPartMapper.countByTaskId(taskId);
        });
    }
}
```

### 5. Docker Compose 配置

```yaml
# docker/docker-compose.monitoring.yml
version: '3.8'

services:
  # Prometheus - 指标收集
  prometheus:
    image: prom/prometheus:latest
    container_name: file-service-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'
    networks:
      - file-service-network
    restart: unless-stopped

  # Grafana - 可视化
  grafana:
    image: grafana/grafana:latest
    container_name: file-service-grafana
    ports:
      - "3100:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_ROOT_URL=http://localhost:3100
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
    depends_on:
      - prometheus
    networks:
      - file-service-network
    restart: unless-stopped

networks:
  file-service-network:
    external: true

volumes:
  prometheus_data:
    name: file_service_prometheus_data
  grafana_data:
    name: file_service_grafana_data
```

### 6. Prometheus 配置

```yaml
# docker/monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'file-service'
    environment: 'development'

scrape_configs:
  - job_name: 'file-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8089']  # Windows/Mac
        labels:
          service: 'file-service'
          instance: 'file-service-1'
    
    # 健康检查
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: host.docker.internal:8089
```

### 7. Grafana 数据源配置

```yaml
# docker/monitoring/grafana/datasources/prometheus.yml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: "15s"
```

### 8. Grafana 仪表板配置

```yaml
# docker/monitoring/grafana/dashboards/dashboard.yml
apiVersion: 1

providers:
  - name: 'Bitmap Monitoring'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

### 9. Bitmap 监控仪表板

创建 `docker/monitoring/grafana/dashboards/bitmap-monitoring.json`:

```json
{
  "dashboard": {
    "title": "Bitmap Upload Monitoring",
    "tags": ["bitmap", "upload"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Bitmap 写入成功率",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0},
        "targets": [
          {
            "expr": "rate(bitmap_write_total{result=\"success\"}[5m]) / rate(bitmap_write_total[5m]) * 100",
            "legendFormat": "成功率 (%)"
          }
        ],
        "yaxes": [
          {"format": "percent", "min": 0, "max": 100}
        ]
      },
      {
        "id": 2,
        "title": "缓存命中率",
        "type": "gauge",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0},
        "targets": [
          {
            "expr": "bitmap_cache_hit_ratio * 100"
          }
        ],
        "options": {
          "showThresholdLabels": false,
          "showThresholdMarkers": true
        },
        "fieldConfig": {
          "defaults": {
            "unit": "percent",
            "min": 0,
            "max": 100,
            "thresholds": {
              "steps": [
                {"value": 0, "color": "red"},
                {"value": 80, "color": "yellow"},
                {"value": 95, "color": "green"}
              ]
            }
          }
        }
      },
      {
        "id": 3,
        "title": "回退次数（每分钟）",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 8},
        "targets": [
          {
            "expr": "rate(bitmap_fallback_total[1m])",
            "legendFormat": "{{reason}}"
          }
        ]
      },
      {
        "id": 4,
        "title": "操作耗时分布",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 8},
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(bitmap_operation_duration_seconds_bucket[5m]))",
            "legendFormat": "P99"
          },
          {
            "expr": "histogram_quantile(0.95, rate(bitmap_operation_duration_seconds_bucket[5m]))",
            "legendFormat": "P95"
          },
          {
            "expr": "histogram_quantile(0.50, rate(bitmap_operation_duration_seconds_bucket[5m]))",
            "legendFormat": "P50"
          }
        ],
        "yaxes": [
          {"format": "s"}
        ]
      },
      {
        "id": 5,
        "title": "同步操作统计",
        "type": "stat",
        "gridPos": {"h": 4, "w": 6, "x": 0, "y": 16},
        "targets": [
          {
            "expr": "rate(bitmap_sync_total[5m])",
            "legendFormat": "同步频率"
          }
        ]
      },
      {
        "id": 6,
        "title": "活跃任务数",
        "type": "stat",
        "gridPos": {"h": 4, "w": 6, "x": 6, "y": 16},
        "targets": [
          {
            "expr": "bitmap_active_tasks"
          }
        ]
      }
    ]
  }
}
```

### 10. 启动脚本

```powershell
# scripts/start-monitoring.ps1
Write-Host "启动 Prometheus + Grafana 监控栈..." -ForegroundColor Green

# 检查 Docker 是否运行
$dockerRunning = docker info 2>$null
if (-not $dockerRunning) {
    Write-Host "错误: Docker 未运行，请先启动 Docker Desktop" -ForegroundColor Red
    exit 1
}

# 启动监控服务
Set-Location -Path "docker"
docker-compose -f docker-compose.monitoring.yml up -d

Write-Host "等待服务启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 检查服务状态
$prometheusStatus = docker ps --filter "name=file-service-prometheus" --format "{{.Status}}"
$grafanaStatus = docker ps --filter "name=file-service-grafana" --format "{{.Status}}"

if ($prometheusStatus -match "Up") {
    Write-Host "✓ Prometheus 已启动" -ForegroundColor Green
} else {
    Write-Host "✗ Prometheus 启动失败" -ForegroundColor Red
}

if ($grafanaStatus -match "Up") {
    Write-Host "✓ Grafana 已启动" -ForegroundColor Green
} else {
    Write-Host "✗ Grafana 启动失败" -ForegroundColor Red
}

Write-Host "`n监控服务访问地址:" -ForegroundColor Cyan
Write-Host "  Prometheus: http://localhost:9090" -ForegroundColor White
Write-Host "  Grafana:    http://localhost:3100 (admin/admin)" -ForegroundColor White
Write-Host "  Metrics:    http://localhost:8089/actuator/prometheus" -ForegroundColor White

# 打开浏览器
Start-Process "http://localhost:3100"
```

### 11. 关键监控指标

| 指标名称 | 类型 | 说明 | PromQL 示例 |
|---------|------|------|------------|
| `bitmap.write` | Counter | Bitmap 写入次数 | `rate(bitmap_write_total[5m])` |
| `bitmap.cache` | Counter | 缓存命中/未命中 | `rate(bitmap_cache_total{result="hit"}[5m])` |
| `bitmap.cache.hit.ratio` | Gauge | 缓存命中率 | `bitmap_cache_hit_ratio * 100` |
| `bitmap.fallback` | Counter | 回退次数 | `rate(bitmap_fallback_total[5m])` |
| `bitmap.sync.total` | Counter | 同步操作次数 | `bitmap_sync_total` |
| `bitmap.sync.parts` | Counter | 同步的分片数 | `rate(bitmap_sync_parts_total[5m])` |
| `bitmap.operation.duration` | Timer | 操作耗时 | `histogram_quantile(0.99, rate(bitmap_operation_duration_seconds_bucket[5m]))` |
| `bitmap.active.tasks` | Gauge | 活跃任务数 | `bitmap_active_tasks` |

### 12. 告警规则（可选）

```yaml
# docker/monitoring/prometheus-alerts.yml
groups:
  - name: bitmap_alerts
    interval: 30s
    rules:
      # 缓存命中率过低
      - alert: BitmapCacheHitRatioLow
        expr: bitmap_cache_hit_ratio < 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Bitmap 缓存命中率过低"
          description: "缓存命中率 {{ $value | humanizePercentage }}，低于 80%"
      
      # 回退次数过多
      - alert: BitmapFallbackHigh
        expr: rate(bitmap_fallback_total[5m]) > 10
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Bitmap 回退次数过多"
          description: "每分钟回退 {{ $value }} 次，Redis 可能不可用"
      
      # 操作耗时过长
      - alert: BitmapOperationSlow
        expr: histogram_quantile(0.99, rate(bitmap_operation_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Bitmap 操作耗时过长"
          description: "P99 耗时 {{ $value }}s，超过 1 秒"
```

### 13. 使用指南

#### 启动监控

```powershell
# 方式 1: 使用脚本
.\scripts\start-monitoring.ps1

# 方式 2: 手动启动
cd docker
docker-compose -f docker-compose.monitoring.yml up -d
```

#### 访问界面

1. **Grafana**: `http://localhost:3100`
   - 用户名: `admin`
   - 密码: `admin`
   - 首次登录会要求修改密码

2. **Prometheus**: `http://localhost:9090`
   - 查询指标
   - 查看目标状态

3. **Metrics 端点**: `http://localhost:8089/actuator/prometheus`
   - 查看原始指标数据

#### 导入仪表板

1. 登录 Grafana
2. 左侧菜单 → Dashboards → Import
3. 上传 `bitmap-monitoring.json` 或输入仪表板 ID
4. 选择 Prometheus 数据源
5. 点击 Import

#### 停止监控

```powershell
cd docker
docker-compose -f docker-compose.monitoring.yml down
```

### 14. 故障排查

#### Prometheus 无法采集指标

```powershell
# 检查 Prometheus 目标状态
# 访问 http://localhost:9090/targets

# 检查 file-service 是否暴露指标
curl http://localhost:8089/actuator/prometheus

# 检查 Docker 网络
docker network inspect file-service-network
```

#### Grafana 无法连接 Prometheus

```powershell
# 进入 Grafana 容器测试连接
docker exec -it file-service-grafana sh
wget -O- http://prometheus:9090/api/v1/query?query=up
```

#### 指标数据不更新

```powershell
# 检查 Prometheus 抓取间隔
# 默认 15 秒，等待一段时间后刷新

# 检查应用日志
docker logs file-service-app

# 手动触发指标更新
curl http://localhost:8089/actuator/prometheus
```
