# file-service 代码 Review（第 2 轮）：修复 Round1 全部 3 项问题

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | commit `4bf5091..1948b86`，11 个文件，252 行新增 / 187 行删除 |
| 变更概述 | P1: `findByHashAndIncrementCount` 改用 `UPDATE ... RETURNING` 返回完整 StorageObject，消除竞态窗口；P2: 所有秒传分支增加 `decrementReferenceCount` 补偿；P3: 依赖 `(app_id, file_hash)` 唯一索引保证；同时修复上一问题 Round2 遗留的 P7（Command DTO）和 P8（cleanupS3Quietly 统一） |
| 审查基准 | `docs/reviews/file-service-code-review-round1-4bf5091.md` |
| 审查日期 | 2026-02-28 |
| 上轮问题数 | 3 项（1 高 / 1 中 / 1 低）→ 逐项复核如下 |

## 上轮问题复核

### P1-高：原子 UPDATE 后 SELECT 竞态窗口 — ✅ 已修复

Mapper 层改用 `UPDATE ... RETURNING` 一次完成引用计数增加和完整对象返回。Repository 接口返回 `Optional<StorageObject>`，4 处调用点全部改为 `instantResult.isPresent()` + `instantResult.get()` 直接使用，不再需要额外 SELECT。竞态窗口已消除。

### P2-中：秒传失败时引用计数无补偿 — ✅ 已修复

4 处秒传调用点均已增加 try-catch 补偿：
- `UploadApplicationService.uploadImage`：缩略图上传失败 + 数据库写入失败两处均调用 `decrementReferenceCount`
- `UploadApplicationService.uploadFile`：数据库写入失败时补偿
- `PresignedUrlService`：FileRecord 保存失败时补偿
- `InstantUploadService`：FileRecord 保存失败时补偿

### P3-低：SQL 缺少 LIMIT 1 — ✅ 已确认

依赖 `(app_id, file_hash)` 唯一索引保证单行匹配，`UPDATE ... RETURNING` 最多返回一行。无需额外 LIMIT 1。

## 附带修复复核（来自事务问题 Round2 遗留）

### P7-中：参数过多 — ✅ 已修复

新增 `ImageUploadCommand` 和 `FileUploadCommand` 两个 DTO，`FileTransactionHelper` 的 `saveImageUploadRecord` / `saveFileUploadRecord` 改为接收单个 Command 对象。

### P8-低：cleanupS3Quietly 重复 — ✅ 已修复

三个 Service 中的私有 `cleanupS3Quietly` 方法已删除，统一收敛到 `FileTransactionHelper.cleanupS3Quietly`（public 方法），各 Service 通过 `transactionHelper.cleanupS3Quietly()` 调用。

## 本轮新发现问题

### P4-中：`cleanupS3Quietly` 放在 `FileTransactionHelper` 职责不匹配

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `FileTransactionHelper` 的职责是「集中管理短事务数据库写入」，但 `cleanupS3Quietly` 是非事务性的 S3 操作，放在这个类里违反单一职责。且该方法注入了 `StorageService`，扩大了事务辅助类的依赖范围 |
| 影响范围 | 类职责边界模糊，后续维护容易混淆 |
| 修正建议 | 将 `cleanupS3Quietly` 移到 `S3StorageService`（或 `StorageService` 接口）中，作为存储层的补偿清理能力 |
| 可选方案 | 无 |

### P5-低：`PresignedUrlService.confirmUpload` 秒传分支的 FileRecord 保存未走 `FileTransactionHelper`

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `PresignedUrlService.confirmUpload` 中 FileRecord 的创建和保存直接在方法内完成，没有通过 `FileTransactionHelper` 的短事务方法，与其他 Service 的模式不一致 |
| 影响范围 | 风格不一致，且该处写库操作没有显式事务保护 |
| 修正建议 | 后续统一改造时收敛到 `FileTransactionHelper`，当前不阻塞 |
| 可选方案 | 无 |

## 问题统计

| 类别 | 数量 |
|------|------|
| 上轮问题 | 3 项（1 高 / 1 中 / 1 低）→ 全部修复 |
| 附带修复 | 2 项（P7、P8）→ 全部修复 |
| 本轮新发现 | 2 项（0 高 / 1 中 / 1 低） |

## 审查结论

上轮 3 项问题全部修复，附带修复了事务问题 Round2 遗留的 P7、P8，改动质量良好：

- `UPDATE ... RETURNING` 彻底消除了秒传竞态窗口，一次 SQL 完成引用计数增加和对象返回
- 4 处秒传分支均有 `decrementReferenceCount` 补偿，与非秒传分支的 S3 补偿对称
- Command DTO 和 cleanupS3Quietly 统一收敛提升了代码可维护性

本轮新发现 2 项均为非阻塞的代码质量问题（P4 职责归属、P5 风格不一致），可在后续迭代中优化。

无阻塞项，可以继续下一个任务。
