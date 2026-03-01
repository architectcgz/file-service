# file-service 代码 Review（s3-db-consistency 第 1 轮）：deleteFile 操作顺序调整与孤立文件清理

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | s3-db-consistency |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | `4f4399f..3e43c53`（2 commits），8 files changed，+330 / -66 |
| 变更概述 | 将 deleteFile() 操作顺序调整为「先删 S3，再短事务更新数据库」，抽取 FileDeleteTransactionHelper 承载事务方法，新增 OrphanedObjectCleanupScheduler 孤立文件清理定时任务 |
| 审查基准 | 项目无独立架构文档目录，以现有代码风格和 CLAUDE.md 规范为基准 |
| 审查日期 | 2026-02-28 |

## 问题清单

### 🔴 高优先级

#### [H1] UploadApplicationService.deleteFile() 存在 TOCTOU 竞态窗口

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/UploadApplicationService.java` 第 345~358 行
- **问题描述**：步骤 4 读取 `referenceCount` 判断是否需要删除 S3（第 349 行），与步骤 6 在事务中执行 `decrementReferenceCount`（第 362 行）之间没有任何锁保护。在并发场景下，两个用户同时删除引用同一 StorageObject 的不同 FileRecord 时，两者都可能读到 `referenceCount = 2`，判定 `shouldDeleteS3 = false`，最终引用计数归零但 S3 对象永远不会被删除。反之，若两者都读到 `referenceCount = 1`，则会重复删除 S3 对象（虽然 S3 删除是幂等的，但逻辑上不正确）。
- **影响范围/风险**：高并发下 S3 对象泄漏（永远不被清理），或引用计数与实际状态不一致。虽然有 OrphanedObjectCleanupScheduler 兜底，但不应依赖兜底机制来处理正常业务流程中的竞态。
- **修正建议**：将引用计数判断和 S3 删除决策合并到一个原子操作中。推荐方案：在事务内先执行 `decrementReferenceCount`，然后在同一事务内查询更新后的引用计数。如果归零，在事务提交后再删除 S3 对象。或者使用 `SELECT ... FOR UPDATE` 加行锁后再判断。示例：

```java
// 方案：事务内先减引用计数，再判断是否归零
@Transactional(rollbackFor = Exception.class)
public DeleteDecision decrementAndCheck(String storageObjectId) {
    storageObjectRepository.decrementReferenceCount(storageObjectId);
    Optional<StorageObject> updated = storageObjectRepository.findById(storageObjectId);
    if (updated.isPresent() && updated.get().canBeDeleted()) {
        // 标记需要删除，但 S3 删除在事务提交后执行
        storageObjectRepository.deleteById(storageObjectId);
        return DeleteDecision.DELETE_S3;
    }
    return DeleteDecision.KEEP_S3;
}
// 调用方：
// DeleteDecision decision = helper.decrementAndCheck(storageObjectId);
// if (decision == DeleteDecision.DELETE_S3) { storageService.delete(path); }
```

#### [H2] FileManagementService.deleteFile() 管理员删除未处理 StorageObject 引用计数

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileManagementService.java` 第 75~96 行；`FileDeleteTransactionHelper.java` 第 64~77 行
- **问题描述**：管理员删除文件时，`updateDatabaseAfterAdminDelete()` 只做了硬删除 FileRecord 和减少租户用量，完全没有处理 StorageObject 的引用计数。删除后 StorageObject 的 `referenceCount` 不会减少，导致引用计数永远无法归零，S3 对象永远不会被清理。
- **影响范围/风险**：管理员每次删除文件都会造成 S3 存储泄漏。OrphanedObjectCleanupScheduler 也无法兜底，因为引用计数不为零，不会被识别为孤立对象。
- **修正建议**：在 `updateDatabaseAfterAdminDelete()` 中补充引用计数递减和零引用清理逻辑，与用户删除保持一致。

#### [H3] OrphanedObjectCleanupScheduler S3 删除失败仍删除数据库记录，导致 S3 泄漏

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/scheduler/OrphanedObjectCleanupScheduler.java` 第 108~113 行
- **问题描述**：`cleanupSingleObject()` 中，当 S3 删除抛出异常时，catch 块仅记录 warn 日志，然后继续执行第 116 行的 `storageObjectRepository.deleteById()`。这意味着 S3 对象仍然存在，但数据库记录已被删除，导致 S3 对象成为真正的孤立对象且再也无法通过数据库追踪到。
- **影响范围/风险**：S3 存储永久泄漏，且无法通过数据库查询发现这些泄漏对象。注释中说"引用计数已为零，保留数据库记录无意义"是错误的——保留记录恰恰是为了下次重试清理。
- **修正建议**：S3 删除失败时应跳过数据库记录删除，保留记录以便下次调度重试。示例：

```java
private void cleanupSingleObject(StorageObject storageObject) {
    String objectId = storageObject.getId();
    String storagePath = storageObject.getStoragePath();

    // 1. 尝试删除 S3 对象
    boolean s3Deleted = false;
    try {
        if (storageService.exists(storagePath)) {
            storageService.delete(storagePath);
            s3Deleted = true;
        } else {
            s3Deleted = true; // S3 对象已不存在，视为删除成功
        }
    } catch (Exception e) {
        log.warn("S3 对象删除失败，保留数据库记录待下次重试: path={}, error={}",
                storagePath, e.getMessage());
    }

    // 2. 仅在 S3 删除成功后才删除数据库记录
    if (s3Deleted) {
        storageObjectRepository.deleteById(objectId);
    }
}
```

### 🟡 中优先级

#### [M1] OrphanedObjectCleanupScheduler 缺少分布式调度保护

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/scheduler/OrphanedObjectCleanupScheduler.java` 第 48 行
- **问题描述**：`@Scheduled` 注解的定时任务在多实例部署时，每个实例都会独立触发执行。多个实例同时查询到相同的孤立对象并尝试删除，虽然不会导致数据错误（S3 删除幂等、数据库删除有行级竞争），但会产生大量无效的 S3 exists/delete 调用和 warn 日志。
- **影响范围/风险**：多实例部署时资源浪费，日志噪音。
- **修正建议**：引入分布式锁（如 Redis 分布式锁或 ShedLock）确保同一时刻只有一个实例执行清理任务。示例：

```java
@Scheduled(cron = "${file-service.cleanup.orphaned.cron:0 30 3 * * *}")
public void cleanupOrphanedObjects() {
    boolean locked = redisLockService.tryLock("cleanup:orphaned-objects", Duration.ofMinutes(30));
    if (!locked) {
        log.info("其他实例正在执行孤立文件清理，跳过本次调度");
        return;
    }
    try {
        doCleanup();
    } finally {
        redisLockService.unlock("cleanup:orphaned-objects");
    }
}
```

#### [M2] OrphanedObjectCleanupScheduler 只处理一批数据，未循环分批

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/scheduler/OrphanedObjectCleanupScheduler.java` 第 53~54 行
- **问题描述**：`cleanupOrphanedObjects()` 只调用了一次 `findZeroReferenceObjects(batchSize)`，如果孤立对象数量超过 `batchSize`（默认 100），剩余的对象要等到下一次调度才能处理。在异常积压场景下（如大规模删除后），清理速度可能跟不上积压速度。
- **影响范围/风险**：孤立对象积压，S3 存储成本持续增长。
- **修正建议**：改为循环分批处理，直到某一批返回空列表为止。同时设置单次调度的最大处理总量上限，防止长时间占用资源。示例：

```java
@Value("${file-service.cleanup.orphaned.max-total:1000}")
private int maxTotal;

private void doCleanup() {
    int totalProcessed = 0;
    while (totalProcessed < maxTotal) {
        List<StorageObject> batch = storageObjectRepository.findZeroReferenceObjects(batchSize);
        if (batch.isEmpty()) break;
        for (StorageObject obj : batch) {
            cleanupSingleObject(obj);
            totalProcessed++;
        }
    }
}
```

#### [M3] OrphanedObjectCleanupScheduler.cleanupSingleObject() 存在 exists + delete 竞态

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/scheduler/OrphanedObjectCleanupScheduler.java` 第 102~107 行
- **问题描述**：先调用 `storageService.exists(storagePath)` 再调用 `storageService.delete(storagePath)`，两次调用之间对象可能被其他进程删除或创建。S3 的 `deleteObject` 本身是幂等的，对不存在的对象调用不会报错，因此 `exists` 检查是多余的，反而引入了额外的 S3 API 调用开销和竞态窗口。
- **影响范围/风险**：每个孤立对象多一次 S3 HEAD 请求，增加延迟和成本；极端情况下 exists 返回 false 但对象实际存在（S3 最终一致性）。
- **修正建议**：直接调用 `storageService.delete(storagePath)`，移除 `exists` 检查。

#### [M4] FileDeleteTransactionHelper 抛出 RuntimeException 而非业务异常

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileDeleteTransactionHelper.java` 第 69 行
- **问题描述**：`updateDatabaseAfterAdminDelete()` 中 `fileRecordRepository.deleteById()` 失败时抛出裸 `RuntimeException("Failed to delete file record from database")`。项目已有 `BusinessException` 等自定义异常体系，此处应使用业务异常以便全局异常处理器正确分类和响应。
- **影响范围/风险**：异常被全局处理器捕获后可能返回 500 而非合理的业务错误码；错误消息为硬编码英文字符串，不符合项目错误消息常量化规范。
- **修正建议**：替换为 `BusinessException`，错误消息提取为常量。

#### [M5] selectZeroReferenceObjects 查询缺少时间保护窗口

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/repository/mapper/StorageObjectMapper.java` 第 104~113 行
- **问题描述**：SQL 条件 `WHERE reference_count <= 0` 没有时间过滤。刚被 `decrementReferenceCount` 置为 0 的记录会立即被定时任务捡到并删除，但此时 `deleteFile()` 的后续步骤（如事务提交后的 S3 删除）可能还在进行中。如果定时任务先删了数据库记录，`deleteFile()` 的事务提交后发现记录已不存在，虽然不会报错，但逻辑上存在干扰。
- **影响范围/风险**：定时任务与正常删除流程互相干扰，极端情况下可能导致重复 S3 删除或日志混乱。
- **修正建议**：增加时间保护窗口，只清理 `updated_at` 早于一定时间（如 1 小时前）的零引用记录。

```sql
WHERE reference_count <= 0
  AND updated_at < DATE_SUB(NOW(), INTERVAL #{graceMinutes} MINUTE)
ORDER BY updated_at ASC
LIMIT #{limit}
```

### 🟢 低优先级

#### [L1] UploadApplicationService.deleteFile() 错误消息中英文混用

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/UploadApplicationService.java` 第 331~337 行
- **问题描述**：第 332 行 `"Access denied: file belongs to different app"` 为英文，第 337 行 `"无权删除该文件"` 为中文。同一方法内错误消息语言不一致。
- **影响范围/风险**：用户体验不一致，前端展示混乱。
- **修正建议**：统一为中文，并提取到错误消息常量类中。

#### [L2] FileDeleteTransactionHelper 日志风格不一致

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileDeleteTransactionHelper.java` 第 53 行、第 71 行、第 75~76 行
- **问题描述**：`updateDatabaseAfterUserDelete()` 中使用中文日志（第 53 行 `"StorageObject 记录已删除"`），而 `updateDatabaseAfterAdminDelete()` 中使用英文日志（第 71 行 `"Deleted file record from database"`、第 75 行 `"Decremented tenant usage for tenant"`）。同一个类内日志语言不统一。
- **影响范围/风险**：日志检索和运维排查时体验不一致。
- **修正建议**：统一为中文日志风格，与项目其他新增代码（如 OrphanedObjectCleanupScheduler）保持一致。

#### [L3] StorageObjectRepository.findAll() 方法已声明但未被使用

- **文件**：`file-service/src/main/java/com/architectcgz/file/domain/repository/StorageObjectRepository.java` 第 72~79 行；`StorageObjectRepositoryImpl.java` 第 68~73 行；`StorageObjectMapper.java` 第 122~130 行
- **问题描述**：`findAll(int offset, int limit)` 方法在 Repository 接口、实现类和 Mapper 中都已定义，但整个变更中没有任何调用方。类注释提到"用于孤立文件清理任务中与 S3 对象进行比对"，但 OrphanedObjectCleanupScheduler 并未实现反向比对功能。
- **影响范围/风险**：死代码，增加维护负担。
- **修正建议**：如果反向比对功能暂不实现，移除该方法，待实际需要时再添加。避免提前引入未使用的接口。

#### [L4] OrphanedObjectCleanupScheduler 使用 @Value 而非 @ConfigurationProperties

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/scheduler/OrphanedObjectCleanupScheduler.java` 第 35~36 行
- **问题描述**：`batchSize` 通过 `@Value` 注入。项目中其他配置（如 `CacheProperties`、`ImageProcessingProperties`）均使用 `@ConfigurationProperties` 模式，此处风格不一致。且 `@Value` 不支持类型安全校验和 IDE 自动补全。
- **影响范围/风险**：配置管理风格不统一，后续扩展清理相关配置时需要逐个添加 `@Value`。
- **修正建议**：创建 `CleanupProperties` 配置类，使用 `@ConfigurationProperties(prefix = "file-service.cleanup.orphaned")` 统一管理。

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 3 |
| 🟡 中 | 5 |
| 🟢 低 | 4 |
| 合计 | 12 |

## 总体评价

变更的整体设计方向是正确的：将 deleteFile() 调整为「先删 S3，再更新数据库」能有效避免数据库已更新但 S3 删除失败导致的不一致问题；抽取 FileDeleteTransactionHelper 解决同类调用 @Transactional 失效也是合理的架构决策；OrphanedObjectCleanupScheduler 作为兜底补偿机制的思路值得肯定。

但当前实现存在 3 个高优先级问题需要优先修复：

1. 用户删除流程中引用计数判断与实际递减之间的 TOCTOU 竞态窗口（H1），这是本次变更最核心的并发安全问题，需要将「读取-判断-递减」合并为原子操作。
2. 管理员删除流程完全遗漏了 StorageObject 引用计数处理（H2），会导致 S3 存储永久泄漏且定时任务无法兜底。
3. 定时任务在 S3 删除失败时仍删除数据库记录（H3），与「先删 S3 再删数据库」的设计原则自相矛盾，会造成不可追踪的 S3 泄漏。

建议修复优先级：H1 > H2 > H3 > M5 > M1 > M2 > M3 > M4 > L1~L4。
