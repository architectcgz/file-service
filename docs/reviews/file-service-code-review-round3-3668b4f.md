# file-service 代码 Review（第 3 轮）：修复 Round2 遗留的 P4、P5

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 3 轮（修复后复审） |
| 审查范围 | commit `3668b4f`，7 个文件，75 行新增 / 43 行删除 |
| 变更概述 | P4: `cleanupS3Quietly` 移至 `StorageService.deleteQuietly`；P5: `PresignedUrlService.confirmUpload` 的 FileRecord 保存收敛到 `FileTransactionHelper.savePresignedUploadRecord` |
| 审查基准 | `docs/reviews/file-service-code-review-round2-1948b86.md` |
| 审查日期 | 2026-02-28 |
| 上轮问题数 | 2 项（0 高 / 1 中 / 1 低）→ 逐项复核如下 |

## 上轮问题复核

### P4-中：`cleanupS3Quietly` 放在 `FileTransactionHelper` 职责不匹配 — ✅ 已修复

`cleanupS3Quietly` 已从 `FileTransactionHelper` 移除，改为 `StorageService` 接口的 `deleteQuietly` default 方法，`S3StorageService` 覆盖实现并添加日志。`FileTransactionHelper` 不再依赖 `StorageService`，职责回归纯数据库事务。4 处调用点全部改为 `storageService.deleteQuietly()` / `s3StorageService.deleteQuietly()`。

### P5-低：`PresignedUrlService.confirmUpload` 未走 `FileTransactionHelper` — ✅ 已修复

FileRecord 创建逻辑已收敛到 `FileTransactionHelper.savePresignedUploadRecord`，`PresignedUrlService` 通过注入 `transactionHelper` 调用，与其他 Service 模式一致。原方法上的 `@Transactional` 已移除。

## 本轮新发现问题

### P6-低：`savePresignedUploadRecord` 参数过多，未使用 Command DTO

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `FileTransactionHelper.savePresignedUploadRecord` 有 6 个 String 参数，而同类的 `saveImageUploadRecord` / `saveFileUploadRecord` 已改用 Command DTO，风格不一致 |
| 影响范围 | 代码风格一致性 |
| 修正建议 | 后续统一时引入 `PresignedUploadCommand` DTO，当前不阻塞 |
| 可选方案 | 无 |

## 问题统计

| 类别 | 数量 |
|------|------|
| 上轮问题 | 2 项（0 高 / 1 中 / 1 低）→ 全部修复 |
| 本轮新发现 | 1 项（0 高 / 0 中 / 1 低） |

## 审查结论

上轮 2 项问题全部修复：

- `deleteQuietly` 归属到 `StorageService` 接口，`FileTransactionHelper` 职责回归纯事务，分层清晰
- `PresignedUrlService` 的 FileRecord 保存收敛到 `FileTransactionHelper`，全局写库路径统一

本轮仅 1 项低级别风格问题（P6 参数未 DTO 化），不阻塞。

问题 2（秒传竞态条件）全部 review 轮次已通过，无阻塞项。
