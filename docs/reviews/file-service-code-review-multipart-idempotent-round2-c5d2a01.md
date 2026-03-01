# file-service 代码 Review（multipart-idempotent 第 2 轮）：分片上传幂等性修复

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | multipart-idempotent |
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | master..fix-multipart-idempotent（2 commits, c5d2a01），5 文件，+223/-50 行 |
| 变更概述 | 分片重复上传时返回已有 ETag 实现幂等，新增分布式锁防并发，ETag 同步写入数据库 |
| 审查基准 | docs/todo.md 问题 4（分片上传幂等性不完整） |
| 审查日期 | 2026-03-01 |
| 上轮问题数 | 7 项（2 高 / 3 中 / 2 低） |

## 问题清单

### 🔴 高优先级

#### [H1] uploadPart() 方法仍标注 @Transactional，分布式锁 + S3 操作在事务内执行

- **文件**：`MultipartUploadService.java` 第 148 行
- **问题描述**：`uploadPart()` 方法保留了 `@Transactional` 注解。方法内部包含：Redis 分布式锁获取/释放、`Thread.sleep()` 轮询等待、S3 `uploadPart` 网络调用。这些操作全部在数据库事务内执行，事务持续时间不可控。
- **影响范围/风险**：与 todo.md 问题 1（事务内包含 S3 操作）同类风险——高并发下数据库连接池耗尽，S3 超时导致事务长时间挂起。`waitAndGetExistingEtag()` 中最多等待 3 秒（6 次 x 500ms），期间数据库连接一直被占用。
- **修正建议**：移除 `uploadPart()` 上的 `@Transactional`，将数据库写操作（`savePart`）封装为独立的短事务方法（参考 fix-s3-db-consistency 分支的 `FileDeleteTransactionHelper` 模式）。

#### [H2] 分布式锁释放不安全——直接 delete 可能误删其他请求持有的锁

- **文件**：`MultipartUploadService.java` 第 183-186 行
- **问题描述**：获取锁时使用 `SETNX + TTL(30s)`，释放时直接 `redisTemplate.delete(lockKey)`。如果 S3 上传耗时超过 30 秒（大分片、网络抖动），锁自动过期后被另一个请求获取，当前请求的 finally 会误删新锁。
- **影响范围/风险**：同一分片被两个请求并发上传到 S3，产生两个不同的 ETag，但数据库只保存后写入的那个。`completeUpload()` 时使用的 ETag 可能与 S3 实际的 ETag 不匹配，导致合并失败。
- **修正建议**：锁 value 使用唯一标识，释放时用 Lua 脚本比较后删除：

```java
String lockValue = UUID.randomUUID().toString();
Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, PART_LOCK_TIMEOUT);
// ...
// finally 中使用 Lua 脚本安全释放
String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
        List.of(lockKey), lockValue);
```

### 🟡 中优先级

#### [M1] waitAndGetExistingEtag() 使用 Thread.sleep() 阻塞线程

- **文件**：`MultipartUploadService.java` 第 199-215 行
- **问题描述**：锁竞争失败时，使用 `Thread.sleep(500)` 轮询 6 次（共 3 秒）等待另一个请求完成上传。这会阻塞 Servlet 线程，高并发下大量线程被阻塞在 sleep 上。
- **影响范围/风险**：Tomcat 默认 200 线程，如果大量分片并发重试，线程池可能被耗尽。
- **修正建议**：考虑两种替代方案：
  1. 直接返回已有 ETag（如果 DB 中已有），否则抛出可重试异常让客户端稍后重试
  2. 如果必须等待，使用异步机制（如 `CompletableFuture` + 超时）而非阻塞线程

```java
// 方案 1：简化处理，不等待
private String getExistingEtagOrRetry(String taskId, int partNumber) {
    Optional<UploadPart> existing = uploadPartRepository
            .findByTaskIdAndPartNumber(taskId, partNumber);
    if (existing.isPresent() && existing.get().getEtag() != null) {
        return existing.get().getEtag();
    }
    // 另一个请求正在上传中，让客户端稍后重试
    throw new BusinessException("分片正在上传中，请稍后重试");
}
```

#### [M2] PART_LOCK_TIMEOUT 硬编码为 30 秒

- **文件**：`MultipartUploadService.java` 第 192 行
- **问题描述**：`PART_LOCK_TIMEOUT = Duration.ofSeconds(30)` 硬编码为常量。分片大小可配置（通过 `MultipartProperties.chunkSize`），大分片的 S3 上传时间可能超过 30 秒。
- **影响范围/风险**：锁超时时间与实际上传时间不匹配，导致锁提前过期，并发安全性失效。
- **修正建议**：将锁超时时间提取到 `MultipartProperties` 配置类中，并根据分片大小设置合理的默认值：

```yaml
file-service:
  multipart:
    part-lock-timeout-seconds: ${PART_LOCK_TIMEOUT:60}
```

#### [M3] savePart 改为同步写数据库，但异步同步逻辑仍保留

- **文件**：`UploadPartRepositoryImpl.java` 第 127-128 行
- **问题描述**：原来的 `savePart()` 流程是「写 Bitmap → 定期异步同步到数据库」，现在改为「写 Bitmap → 同步写数据库」。但 `asyncSyncToDatabase()` 方法和 `shouldSync()` 判断逻辑虽然在 `savePart` 中被移除了调用，方法本身仍保留在类中（第 440-485 行的 `syncBitmapToDatabase` 方法）。这些残留代码可能造成混淆。
- **影响范围/风险**：代码可维护性问题，新开发者可能不确定同步策略到底是同步还是异步。
- **修正建议**：如果异步同步逻辑不再需要，应移除相关方法和配置；如果仍作为兜底保留，需在注释中明确说明。

### 🟢 低优先级

#### [L1] upsert SQL 中 uploaded_at 被无条件覆盖

- **文件**：`UploadPartMapper.java` 第 52-62 行
- **问题描述**：`upsert` 的 `ON CONFLICT DO UPDATE SET uploaded_at = EXCLUDED.uploaded_at` 会在重复上传时更新 `uploaded_at` 为当前时间。语义上，`uploaded_at` 应该记录首次成功上传的时间，而非最后一次幂等命中的时间。
- **影响范围/风险**：审计和排查时 `uploaded_at` 不反映真实的首次上传时间。
- **修正建议**：移除 `uploaded_at` 的更新，或改为只在 etag 为 null 时更新：

```sql
ON CONFLICT (task_id, part_number) DO UPDATE SET
    etag = COALESCE(upload_parts.etag, EXCLUDED.etag),
    size = COALESCE(upload_parts.size, EXCLUDED.size)
```

#### [L2] findByTaskIdAndPartNumber 缺少索引确认

- **文件**：`UploadPartMapper.java` 第 114-120 行
- **问题描述**：`selectByTaskIdAndPartNumber` 查询 `WHERE task_id = ? AND part_number = ?`，依赖 `(task_id, part_number)` 的唯一约束索引。从 `insertOrIgnore` 的 `ON CONFLICT (task_id, part_number)` 可以推断该约束存在，但建议确认数据库 migration 中已创建。
- **影响范围/风险**：如果索引不存在，高并发下每次幂等检查都是全表扫描。
- **修正建议**：确认 migration 脚本中 `upload_parts` 表有 `UNIQUE (task_id, part_number)` 约束。

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 2 |
| 🟡 中 | 3 |
| 🟢 低 | 2 |
| 合计 | 7 |

## 总体评价

幂等性的核心修复思路正确——重复分片返回已有 ETag 而非抛异常，新增 `findByTaskIdAndPartNumber` 查询和 `upsert` SQL 保证 ETag 持久化。分布式锁的引入也是合理的防御措施。

主要问题集中在两点：一是 `@Transactional` 未移除导致分布式锁和 S3 操作在事务内执行（长事务风险），二是分布式锁的释放不安全（直接 delete 可能误删）。这两个高优先级问题需要修复后再合并。

结论：**需修复后合并**。
