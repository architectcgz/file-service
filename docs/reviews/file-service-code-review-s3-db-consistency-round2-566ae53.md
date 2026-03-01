# file-service 代码 Review（s3-db-consistency 第 2 轮）：调整删除顺序 + 孤立文件清理定时任务

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | s3-db-consistency |
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | master..fix-s3-db-consistency（3 commits, 566ae53），11 文件，+420/-71 行 |
| 变更概述 | 修复 deleteFile() 操作顺序（先事务内原子递减引用计数再删 S3），新增 OrphanedObjectCleanupScheduler 兜底清理 |
| 审查基准 | docs/todo.md 问题 3（S3 与数据库状态不一致） |
| 审查日期 | 2026-03-01 |
| 上轮问题数 | 12 项（3 高 / 5 中 / 4 低） |

## 问题清单

### 🔴 高优先级

#### [H1] deleteFile() 操作顺序与 todo.md 方案不一致，引入新的不一致风险

- **文件**：`FileDeleteTransactionHelper.java` 第 72-95 行，`UploadApplicationService.java` 第 306-322 行
- **问题描述**：todo.md 方案一明确要求「先删 S3，再短事务更新数据库」——S3 删除失败则整个操作中止，数据库状态不变。但实际实现是「先事务内递减引用计数并删除 StorageObject 记录，事务提交后再删 S3」。如果事务提交成功但 S3 删除失败（网络超时、S3 故障），数据库中 StorageObject 记录已被删除，S3 文件变成无法追踪的孤立文件。
- **影响范围/风险**：这正是 todo.md 问题 3 要解决的核心问题——S3 孤立文件无法追踪。当前实现虽然有定时任务兜底，但定时任务清理的是 `reference_count <= 0` 的记录，而此场景下 StorageObject 记录已被删除，定时任务也无法覆盖。
- **修正建议**：按 todo.md 方案一调整为「先删 S3，再短事务更新数据库」：

```java
// UploadApplicationService.deleteFile()
public void deleteFile(String appId, String fileRecordId, String userId) {
    // 1. 查询校验（略）

    // 2. 判断是否需要删除 S3（事务外读取引用计数）
    StorageObject storageObject = storageObjectRepository.findById(storageObjectId).orElseThrow();
    boolean shouldDeleteS3 = storageObject.getReferenceCount() <= 1;

    // 3. 先删 S3（失败则中止，数据库不变）
    if (shouldDeleteS3) {
        storageService.delete(storageObject.getStoragePath());
    }

    // 4. 短事务更新数据库
    deleteTransactionHelper.updateDatabaseAfterUserDelete(appId, fileRecordId, storageObjectId, fileSize);
}
```

#### [H2] FileDeleteTransactionHelper 的 @Transactional 在同层 Service 调用时可能不生效

- **文件**：`FileDeleteTransactionHelper.java` 第 72 行、第 107 行
- **问题描述**：`FileDeleteTransactionHelper` 被标注为 `@Service`，由 `UploadApplicationService` 和 `FileManagementService` 通过依赖注入调用，这种跨 Bean 调用 `@Transactional` 是生效的。但需要确认 `UploadApplicationService.deleteFile()` 方法上已移除 `@Transactional`（已确认移除），否则外层事务会包裹 S3 操作，回到长事务问题。这一点实现是正确的，但 `FileDeleteTransactionHelper` 中 `updateDatabaseAfterUserDelete` 先 `decrementReferenceCount` 再 `findById` 查询更新后的值，存在依赖同一事务内可见性的隐含假设——在 PostgreSQL 默认 READ COMMITTED 隔离级别下，同一事务内的写操作对后续读是可见的，这没问题。但如果 `decrementReferenceCount` 是通过 MyBatis 的 `@Update` 实现，需确认返回后 `findById` 能读到更新后的值。
- **影响范围/风险**：如果隔离级别或 ORM 缓存导致读不到最新值，`canBeDeleted()` 判断可能错误，导致 S3 文件永远不被删除。
- **修正建议**：将 `decrementReferenceCount` 改为返回更新后的引用计数值，避免额外查询：

```java
// StorageObjectMapper
@Update("UPDATE storage_objects SET reference_count = reference_count - 1, updated_at = NOW() WHERE id = #{id} AND reference_count > 0 RETURNING reference_count")
int decrementAndGetReferenceCount(String id);
```

或者使用 `decrementReferenceCount` 返回受影响行数 + 单独的 `SELECT ... FOR UPDATE` 确保读到最新值。

### 🟡 中优先级

#### [M1] 孤立清理定时任务的分布式锁释放存在提前释放风险

- **文件**：`OrphanedObjectCleanupScheduler.java` 第 56-69 行
- **问题描述**：使用 `SETNX + TTL` 获取锁，finally 中直接 `delete` 释放。如果任务执行时间超过 `lockTimeoutSeconds`（默认 1800s），锁已自动过期被其他实例获取，此时 finally 中的 `delete` 会误删其他实例持有的锁。
- **影响范围/风险**：多实例并发执行清理任务，可能重复删除 S3 对象（S3 delete 幂等所以不会出错），但会产生不必要的 S3 API 调用和日志噪音。
- **修正建议**：锁的 value 使用唯一标识（如 UUID），释放时用 Lua 脚本比较后删除：

```java
String lockValue = UUID.randomUUID().toString();
Boolean locked = stringRedisTemplate.opsForValue()
        .setIfAbsent(CLEANUP_LOCK_KEY, lockValue, lockTimeout);
// ...
// finally 中
String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
        List.of(CLEANUP_LOCK_KEY), lockValue);
```

#### [M2] 孤立清理任务缺少对 S3 真实孤立文件的扫描

- **文件**：`OrphanedObjectCleanupScheduler.java` 整体
- **问题描述**：todo.md 方案二要求「列出 S3 中最近 N 天创建的对象，与数据库比对，数据库中不存在的 S3 对象标记为孤立」。当前实现只清理数据库中 `reference_count <= 0` 的记录，不覆盖「数据库记录已删除但 S3 文件仍在」的场景（正是 H1 中描述的风险）。
- **影响范围/风险**：上传成功但数据库写入失败、或 H1 场景中事务提交后 S3 删除失败，都会产生数据库无记录的 S3 孤立文件，当前定时任务无法清理。
- **修正建议**：增加 S3 ListObjects 扫描逻辑，与数据库比对后清理真正的孤立文件。可以作为独立的定时任务实现，与当前的数据库零引用清理并行。

#### [M3] FileErrorMessages 常量类范围过窄

- **文件**：`FileErrorMessages.java` 第 1-23 行
- **问题描述**：只定义了 3 个常量（`FILE_APP_MISMATCH`、`FILE_DELETE_FORBIDDEN`、`FILE_RECORD_DELETE_FAILED`），但 `UploadApplicationService.deleteFile()` 中仍有硬编码的错误消息（如 `"上传任务不存在"`、`"无权操作该上传任务"` 等未替换）。这个分支的 scope 是 S3 一致性修复，不强求全量替换，但既然引入了常量类，建议至少覆盖本次变更涉及的所有错误消息。
- **影响范围/风险**：代码风格不一致，部分用常量部分用硬编码。
- **修正建议**：将本次变更涉及的所有错误消息统一收敛到 `FileErrorMessages`，或者将常量类的引入留给 fix-error-messages 分支统一处理，本分支不引入。

### 🟢 低优先级

#### [L1] 清理任务的 CLEANUP_LOCK_KEY 硬编码

- **文件**：`OrphanedObjectCleanupScheduler.java` 第 42 行
- **问题描述**：`CLEANUP_LOCK_KEY = "file-service:cleanup:orphaned-objects:lock"` 直接硬编码在类中，未通过统一的 RedisKeys 工具类管理。
- **影响范围/风险**：与项目中已有的 `FileRedisKeys`、`UploadRedisKeys` 等工具类风格不一致。
- **修正建议**：新增 `CleanupRedisKeys` 工具类或在已有工具类中增加清理相关的 key 定义。

#### [L2] FileManagementService.deleteFile() 缺少 S3 删除失败的异常处理

- **文件**：`FileManagementService.java`（fix-s3-db-consistency 分支）第 78-82 行
- **问题描述**：管理员删除路径中，`storageService.delete(s3PathToDelete)` 如果抛异常，会导致整个方法失败，但数据库事务已提交。虽然定时任务可以兜底，但缺少 warn 级别日志记录这种不一致状态。
- **影响范围/风险**：S3 删除失败时缺少明确的告警日志，运维难以感知。
- **修正建议**：

```java
if (s3PathToDelete != null) {
    try {
        storageService.delete(s3PathToDelete);
        log.info("S3 对象已删除: path={}", s3PathToDelete);
    } catch (Exception e) {
        log.warn("S3 对象删除失败，等待定时任务清理: path={}", s3PathToDelete, e);
        // 不抛异常，数据库已更新成功
    }
}
```

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 2 |
| 🟡 中 | 3 |
| 🟢 低 | 2 |
| 合计 | 7 |

## 总体评价

整体思路正确——抽取 `FileDeleteTransactionHelper` 解决同类内部 `@Transactional` 不生效的问题，引入 `CleanupProperties` 配置化清理参数，`OrphanedObjectCleanupScheduler` 的分批处理和保护窗口设计合理。

核心问题在于操作顺序与 todo.md 方案不一致：当前是「先事务删库，后删 S3」，而方案要求「先删 S3，后短事务删库」。这导致 S3 删除失败时数据库记录已丢失，孤立文件无法被定时任务追踪。需要调整操作顺序后再合并。

结论：**需修复后合并**。
