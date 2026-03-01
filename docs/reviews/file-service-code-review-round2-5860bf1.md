# file-service 代码 Review（第 2 轮）：修复 Review Round1 全部 6 项问题

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | commit `5860bf1`，4 个文件，296 行新增 / 189 行删除 |
| 变更概述 | 新建 `FileTransactionHelper` 集中管理短事务方法，解决 Spring 代理失效问题；修复 deleteFile 并发窗口；补齐分片上传补偿清理；清理重复注释和无效查询 |
| 审查基准 | `docs/architecture/core-workflows.md`、`docs/reviews/file-service-code-review-round1-a582a60.md` |
| 审查日期 | 2026-02-28 |
| 上轮问题数 | 6 项（2 高 / 2 中 / 2 低）→ 逐项复核如下 |

## 上轮问题复核

### P1-高：Spring 代理失效 — ✅ 已修复

新建 `FileTransactionHelper`（独立 `@Service`），所有短事务方法集中到该类。`DirectUploadService`、`MultipartUploadService`、`UploadApplicationService` 通过注入 `transactionHelper` 调用，`@Transactional` 走 Spring 代理，事务生效。原来各类中的 `protected save*` 方法已全部删除。

### P2-高：deleteFile 并发窗口 — ✅ 已修复

`deleteFileInTransaction` 返回值从 `boolean` 改为 `String`（S3 路径）。当 `canBeDeleted` 为 true 时，在事务内执行 `storageObjectRepository.deleteById()`，外层只负责 S3 物理删除。消除了事务提交后到 deleteById 之间的并发窗口。

### P3-中：Javadoc 注释重复 — ✅ 已修复

`MultipartUploadService:202` 和 `DirectUploadService:305` 的重复 Javadoc 已合并为一段，旧的 `@param/@return` 注释保留，新增说明整合到同一个 Javadoc 块中。

### P4-中：分片上传缺少补偿清理 — ✅ 已修复

`DirectUploadService.completeDirectUpload` 和 `MultipartUploadService.completeUpload` 均已在调用 `transactionHelper.save*Record` 外层加了 try-catch，数据库写入失败时调用 `cleanupS3Quietly` 补偿清理已合并的 S3 文件。

### P5-低：`completedPartNumbers` 无效查询 — ✅ 已修复

`MultipartUploadService:234` 的 `findCompletedPartNumbers` 调用已删除。

### P6-低：Builder 链式调用可读性 — ✅ 已修复

`UploadApplicationService.uploadFile` 中 `StorageObject.builder()` 已恢复为每行一个字段的风格。`FileTransactionHelper` 中所有 Builder 调用也保持了每行一个字段的一致风格。

## 本轮新发现问题

### P7-中：`saveImageUploadRecord` / `saveFileUploadRecord` 参数过多，建议引入 DTO

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `FileTransactionHelper.saveImageUploadRecord` 有 9 个参数，`saveFileUploadRecord` 有 8 个参数。参数过多导致调用方容易传错顺序（多个 String 类型参数相邻），可读性差 |
| 影响范围 | 代码可维护性，调用方容易因参数顺序错误引入隐蔽 bug |
| 修正建议 | 引入 `ImageUploadCommand` / `FileUploadCommand` 等 DTO 封装参数，或使用 Builder 模式 |
| 可选方案 | 无 |

### P8-低：`cleanupS3Quietly` 在三个 Service 中重复定义

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `DirectUploadService`、`MultipartUploadService`、`UploadApplicationService` 各自定义了一份 `cleanupS3Quietly` 私有方法，逻辑完全相同 |
| 影响范围 | 代码重复，后续修改需同步三处 |
| 修正建议 | 提取到公共位置（如 `S3StorageService` 或新建工具类），三个 Service 统一调用 |
| 可选方案 | 无 |

## 问题统计

| 类别 | 数量 |
|------|------|
| 上轮问题 | 6 项（2 高 / 2 中 / 2 低）→ 全部修复 |
| 本轮新发现 | 2 项（0 高 / 1 中 / 1 低） |

## 审查结论

上轮 6 项问题全部修复，核心改动正确：

- `FileTransactionHelper` 作为独立 `@Service` 彻底解决了 Spring 代理失效问题
- `deleteFileInTransaction` 在事务内完成 StorageObject 记录删除，消除并发窗口
- 分片上传补偿清理已补齐，与 `uploadImage`/`uploadFile` 保持一致

本轮新发现 2 项均为非阻塞的代码质量问题（P7 参数过多、P8 方法重复），不影响正确性，可在后续迭代中优化。

本轮无阻塞项，可以继续下一个任务。
