# file-service 代码 Review（multipart-idempotent 第 1 轮）：分片上传重复提交幂等改造

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | multipart-idempotent |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit fbe36f0，4 个文件，+70/-7 行 |
| 变更概述 | MultipartUploadService.uploadPart() 检测到重复分片时，不再抛异常，改为查询已有 ETag 并返回，实现幂等 |
| 审查基准 | docs/architecture/core-workflows.md, docs/architecture/layered-architecture.md |
| 审查日期 | 2026-02-28 |

## 问题清单

### 🔴 高优先级

#### [H1] Bitmap 模式下幂等查询必然失败：savePart 只写 Bitmap 不写数据库，导致 findByTaskIdAndPartNumber 查不到 ETag

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/MultipartUploadService.java` 第 176-187 行
- **问题描述**：
  幂等逻辑依赖 `findByTaskIdAndPartNumber` 从数据库查询已有分片的 ETag。但在 Bitmap 模式下（默认模式），`savePart` 只写 Redis Bitmap，不写数据库。即使触发了定期异步同步（`asyncSyncToDatabase`），同步逻辑也只写入 `partNumber`、`taskId`、`uploadedAt`，**不写入 `etag` 和 `size`**（见 `UploadPartRepositoryImpl` 第 482-489 行）。

  因此，重复分片请求的执行路径几乎必然是：
  1. Bitmap 检测到分片已上传 → 进入幂等分支
  2. 查询数据库 → 无记录（或有记录但 etag 为 null）
  3. 走到 fallback 分支 → 重新上传到 S3 → 重新写入 savePart

  这意味着幂等改造在 Bitmap 模式下**完全无效**，每次重复请求仍会上传到 S3。

- **影响范围/风险**：
  - 幂等性形同虚设，重复请求仍会产生 S3 写入开销
  - S3 同一 uploadId 下同一 partNumber 重复上传会覆盖前一次的数据，返回新的 ETag，可能导致 completeUpload 时 ETag 不一致

- **修正建议**：
  `savePart` 在 Bitmap 模式下也必须同步写入数据库（至少写入 etag），或者改用 `insertOrIgnore`（`ON CONFLICT DO UPDATE SET etag = EXCLUDED.etag`）确保 ETag 始终持久化。推荐方案：

  ```java
  // savePart 方法中，Bitmap 写入成功后，追加数据库写入（使用 upsert 保证幂等）
  try {
      String bitmapKey = UploadRedisKeys.partsBitmap(part.getTaskId());
      long bitOffset = UploadRedisKeys.getBitOffset(part.getPartNumber());
      redisTemplate.opsForValue().setBit(bitmapKey, bitOffset, true);
      redisTemplate.expire(bitmapKey, Duration.ofHours(bitmapProperties.getExpireHours()));

      // 同步写入数据库，确保 ETag 持久化（幂等查询依赖此数据）
      savePartToDatabase(part);  // 需改用 insertOrIgnore 或 upsert

      metrics.recordWriteSuccess();
  } catch (DataAccessException e) {
      // ...
  }
  ```

  同时需要将 Mapper 的 `insert` 改为 `insertOrIgnore`（或新增 upsert 方法），避免重复写入时主键冲突。

#### [H2] 并发竞争：两个相同分片请求同时通过 Bitmap 检查后会重复上传到 S3

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/MultipartUploadService.java` 第 174-204 行
- **问题描述**：
  `uploadPart` 方法的幂等检查（第 175-187 行）和实际上传（第 190 行）之间没有任何互斥机制。当两个相同 `(taskId, partNumber)` 的请求并发到达时：
  1. 请求 A 和请求 B 同时执行 `findCompletedPartNumbers`，均返回"未上传"
  2. 两者都跳过幂等分支，各自上传到 S3
  3. 两者都执行 `savePart`，后写入的覆盖先写入的 ETag

  S3 的 uploadPart 是 last-write-wins，两次上传返回不同的 ETag，但数据库只保留其中一个。如果 completeUpload 使用的 ETag 与 S3 实际存储的不一致，会导致 completeMultipartUpload 失败。

- **影响范围/风险**：
  - 并发重复上传浪费 S3 带宽
  - ETag 不一致可能导致 completeUpload 失败（S3 会校验 ETag）

- **修正建议**：
  对同一 `(taskId, partNumber)` 加分布式锁，或在数据库层面使用 `INSERT ... ON CONFLICT DO NOTHING` + 检查影响行数来实现乐观锁。推荐轻量方案：

  ```java
  // 在 uploadPart 方法中，先尝试 insertOrIgnore 占位，成功才上传
  // 或者使用 Redis 分布式锁：
  String lockKey = "upload:part:lock:" + taskId + ":" + partNumber;
  boolean locked = redisLock.tryLock(lockKey, Duration.ofSeconds(30));
  if (!locked) {
      // 等待另一个请求完成后查询 ETag 返回
      // ...
  }
  ```

### 🟡 中优先级

#### [M1] findByTaskIdAndPartNumber 异常时静默返回 empty，导致幂等失败被掩盖为"重新上传"

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/repository/UploadPartRepositoryImpl.java` 第 163-182 行
- **问题描述**：
  `findByTaskIdAndPartNumber` 在 catch 块中捕获所有异常并返回 `Optional.empty()`。当数据库查询因连接池耗尽、超时等原因失败时，调用方会误判为"数据库无 ETag 记录"，进而重新上传到 S3。这将数据库故障静默转化为重复上传，既浪费资源又难以排查。

- **影响范围/风险**：
  - 数据库异常被吞掉，运维无法通过告警发现问题
  - 本应快速失败的场景变成了静默重试

- **修正建议**：
  区分"记录不存在"和"查询失败"两种情况。查询失败时应向上抛出异常，让调用方决定是否重试或降级：

  ```java
  @Override
  public Optional<UploadPart> findByTaskIdAndPartNumber(String taskId, int partNumber) {
      UploadPartPO po = uploadPartMapper.selectByTaskIdAndPartNumber(taskId, partNumber);
      if (po == null) {
          return Optional.empty();
      }
      return Optional.of(convertToModel(po));
      // 不再 catch Exception，让数据库异常自然传播
  }
  ```

#### [M2] fallback 分支重新上传后会产生重复的数据库记录

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/MultipartUploadService.java` 第 184-204 行
- **问题描述**：
  当 Bitmap 有记录但数据库无 ETag 时，代码走 fallback 分支重新上传到 S3，然后执行 `savePart`。但 `savePart` 内部的 Bitmap 模式会再次 `SETBIT`（已经是 1，无副作用），然后可能触发异步同步。如果此时数据库中已有该分片的记录（由之前的异步同步写入，但 etag 为 null），`savePart` 中的 `savePartToDatabase` 使用的是 `insert`（非 `insertOrIgnore`），会因唯一约束 `(task_id, part_number)` 冲突而抛异常。

- **影响范围/风险**：
  - fallback 路径下可能因主键冲突导致上传失败
  - 用户看到的是"写入数据库失败"而非预期的幂等返回

- **修正建议**：
  `savePartToDatabase` 方法应统一使用 `insertOrIgnore` 或 upsert（`ON CONFLICT DO UPDATE SET etag = EXCLUDED.etag, size = EXCLUDED.size`），确保重复写入不会报错且能更新 ETag：

  ```java
  private void savePartToDatabase(UploadPart part) {
      UploadPartPO po = convertToPO(part);
      uploadPartMapper.upsert(po);  // ON CONFLICT (task_id, part_number) DO UPDATE SET etag, size
  }
  ```

#### [M3] UploadPartRepositoryImpl.findByTaskIdAndPartNumber 中 PO→Model 转换逻辑重复，未复用已有的转换模式

- **文件**：`file-service/src/main/java/com/architectcgz/file/infrastructure/repository/UploadPartRepositoryImpl.java` 第 169-176 行
- **问题描述**：
  新增的 `findByTaskIdAndPartNumber` 方法中手写了 PO→Domain Model 的转换逻辑（使用 Builder），但同文件中已有 `convertToPO`（Model→PO）方法。缺少对应的 `convertToModel`（PO→Model）方法，导致转换逻辑散落在业务方法内部，后续新增查询方法时容易遗漏字段。

- **影响范围/风险**：
  - 字段映射不一致风险（新增字段时容易漏改）
  - 违反 DRY 原则

- **修正建议**：
  提取 `convertToModel(UploadPartPO po)` 私有方法，与 `convertToPO` 对称：

  ```java
  private UploadPart convertToModel(UploadPartPO po) {
      return UploadPart.builder()
              .id(po.getId())
              .taskId(po.getTaskId())
              .partNumber(po.getPartNumber())
              .etag(po.getEtag())
              .size(po.getSize())
              .uploadedAt(po.getUploadedAt())
              .build();
  }
  ```

### 🟢 低优先级

#### [L1] 幂等命中日志输出了完整 ETag 值，存在信息泄露风险

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/MultipartUploadService.java` 第 180-181 行
- **问题描述**：
  `log.info("分片幂等命中，返回已有 ETag: taskId={}, partNumber={}, etag={}", ...)` 将完整的 ETag 值打印到 INFO 级别日志。ETag 是 S3 返回的 MD5 哈希，虽然不算高敏感数据，但在 INFO 级别大量输出会增加日志体积，且不利于日志脱敏策略的统一管理。

- **影响范围/风险**：日志体积增大，不符合最小信息原则

- **修正建议**：
  将 ETag 输出降级为 DEBUG，或只输出前 8 位：

  ```java
  log.info("分片幂等命中: taskId={}, partNumber={}", taskId, partNumber);
  log.debug("幂等命中 ETag 详情: taskId={}, partNumber={}, etag={}", taskId, partNumber, existingPart.get().getEtag());
  ```

#### [L2] 日志风格不统一：新增代码使用中文日志，原有代码使用英文日志

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/MultipartUploadService.java` 第 180-186 行
- **问题描述**：
  新增的幂等分支日志使用中文（`"分片幂等命中，返回已有 ETag"`、`"分片已在 Bitmap 中但数据库无 ETag 记录，重新上传"`），而同方法内原有日志使用英文（第 146 行 `"Uploading part {} for task: {}"`、第 191 行 `"Uploaded part {} with ETag: {}"`）。同一方法内中英文混用降低了日志的可检索性。

- **影响范围/风险**：日志风格不一致，影响 grep/ELK 检索效率

- **修正建议**：
  与项目现有风格保持一致。`MultipartUploadService` 中原有日志均为英文，建议新增日志也使用英文：

  ```java
  log.info("Part idempotent hit: taskId={}, partNumber={}", taskId, partNumber);
  log.info("Part in bitmap but no ETag in DB, re-uploading: taskId={}, partNumber={}", taskId, partNumber);
  ```

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 2 |
| 🟡 中 | 3 |
| 🟢 低 | 2 |
| 合计 | 7 |

## 风险前置检查清单

| 检查项 | 结果 |
|--------|------|
| 幂等性 | ❌ Bitmap 模式下幂等查询必然失败（H1），并发场景无互斥（H2） |
| 并发竞争 | ❌ 同一分片并发上传无锁保护（H2） |
| 超时与重试 | ⚠️ 未涉及，但 fallback 重新上传可能因 S3 超时导致重试风暴 |
| 补偿/回滚 | ✅ 未涉及，不在本次变更范围 |
| 可观测性 | ⚠️ 幂等命中/fallback 有日志，但缺少 metrics 埋点（如幂等命中率） |

## 总体评价

本次变更的意图正确——将分片重复提交从抛异常改为幂等返回，方向值得肯定。新增的 Mapper 查询方法和 Repository 接口设计清晰，分层也符合项目规范。

但核心问题在于：幂等查询依赖数据库中的 ETag，而 Bitmap 模式下 `savePart` 不写数据库（或异步同步时不写 ETag），导致幂等逻辑在默认运行模式下完全无效。这是一个架构层面的数据流断裂，必须在合并前修复。

建议修复优先级：H1 → H2 → M2 → M1 → M3 → L1 → L2。其中 H1 是根因，修复后 M2 的问题也会随之简化。
