# file-service 代码 Review（第 1 轮）：调整 deleteFile 为先删 S3 再更新数据库，新增孤立对象清理定时任务

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit `d45119b`，8 个文件，206 行新增 / 45 行删除 |
| 变更概述 | deleteFile 改为「先删 S3 再短事务更新数据库」；新增 `OrphanedObjectCleanupScheduler` 定时清理引用计数为 0 的孤立 StorageObject；附带修复上轮 P6（PresignedUploadCommand DTO） |
| 审查基准 | `docs/architecture/core-workflows.md` |
| 审查日期 | 2026-02-28 |

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `UploadApplicationService.java` | deleteFile 改为先删 S3 再短事务更新数据库 |
| `FileTransactionHelper.java` | `deleteFileInTransaction` 改为接收 `deleteStorageObject` 布尔参数；`savePresignedUploadRecord` 改用 Command DTO |
| `PresignedUrlService.java` | 改用 `PresignedUploadCommand` DTO |
| `PresignedUploadCommand.java` | 新增预签名上传命令 DTO |
| `OrphanedObjectCleanupScheduler.java` | 新增孤立对象清理定时任务 |
| `StorageObjectRepository.java` | 新增 `findOrphanedObjects` 接口 |
| `StorageObjectRepositoryImpl.java` | 实现 `findOrphanedObjects` |
| `StorageObjectMapper.java` | 新增孤立对象查询 SQL |

## 问题列表

### P1-高：deleteFile 中「先查引用计数再删 S3」存在 TOCTOU 竞态

| 字段 | 内容 |
|------|------|
| 问题级别 | 高 |
| 问题描述 | `deleteFile` 在事务外查询 `storageObject.getReferenceCount() <= 1` 判断是否需要删 S3，然后才执行 S3 删除和数据库事务。两个并发请求可能同时读到 `referenceCount = 2`，都判断为 `shouldDeleteS3 = false`，各自在事务内 decrement 后引用计数归零，但 S3 文件没有被任何一方删除，产生永久孤立文件 |
| 影响范围 | 并发删除同一 StorageObject 的最后两个引用时必现 |
| 修正建议 | 在事务内用 `SELECT ... FOR UPDATE` 锁定 StorageObject 行后判断引用计数，事务提交后再根据结果决定是否删 S3。或者不在外层判断，统一由事务内 decrement 后检查 `canBeDeleted`，返回需要删除的 S3 路径（回到之前的方案，但保留孤立清理定时任务兜底） |
| 可选方案 | 无 |

### P2-中：孤立清理定时任务的 `cleanupSingleObject` 非原子，可能重复清理

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `cleanupSingleObject` 先删 S3 再删数据库记录，两步之间没有原子保护。多实例部署时，两个节点可能同时查到同一批孤立对象并发执行清理，导致 S3 重复删除（虽然幂等）和 deleteById 返回 0（无害但浪费）。更关键的是：如果 S3 删除成功但 deleteById 失败，下次扫描会再次尝试删 S3（此时文件已不存在，S3 会报 404） |
| 影响范围 | 多实例部署场景 |
| 修正建议 | ① 加分布式锁（如 Redis SETNX）确保单实例执行；② 或在 deleteById 前用 `DELETE WHERE id = ? AND reference_count = 0` 做乐观删除，返回 0 则跳过 |
| 可选方案 | 无 |

### P3-中：`deleteFile` 先删 S3 后数据库失败时，用户可通过 URL 访问已删除的文件

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | S3 删除成功但数据库事务失败时，FileRecord 状态仍为 COMPLETED，用户查询文件列表仍能看到该文件，但实际 S3 文件已不存在，访问会 404。这比「S3 有孤立文件但数据库已标记删除」的不一致更难被用户理解 |
| 影响范围 | 数据库事务失败场景下的用户体验 |
| 修正建议 | 这是「先删 S3 再更新数据库」方案的固有取舍，建议在注释中明确说明此风险，并确保孤立清理定时任务能覆盖此场景（当前定时任务只清理 `reference_count = 0` 的 StorageObject，不覆盖此情况） |
| 可选方案 | 无 |

### P4-低：定时任务配置项未提取到 Properties 类

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `OrphanedObjectCleanupScheduler` 使用 `@Value` 注入 3 个配置项（cron、threshold-hours、batch-size），按全局 CLAUDE.md 规范应通过 `@ConfigurationProperties` 管理 |
| 影响范围 | 代码规范一致性 |
| 修正建议 | 提取为 `StorageCleanupProperties` 配置类 |
| 可选方案 | 无 |

## 问题统计

| 级别 | 数量 | 编号 |
|------|------|------|
| 高 | 1 | P1 |
| 中 | 2 | P2、P3 |
| 低 | 1 | P4 |
| 合计 | 4 | — |

## 审查结论

孤立对象清理定时任务的引入方向正确，为最终一致性提供了兜底。但 deleteFile 改为「先删 S3 再更新数据库」后引入了 TOCTOU 竞态（P1），是阻塞项。

P1 的核心问题：事务外读取 `referenceCount` 判断是否删 S3，并发场景下两个请求都读到 `count = 2` 都不删，最终引用计数归零但 S3 文件无人清理。建议回到「事务内 decrement + 判断 canBeDeleted → 返回 S3 路径 → 事务外删 S3」的方案，配合孤立清理定时任务兜底。

P2（定时任务多实例并发）和 P3（先删 S3 后 DB 失败的用户体验）建议同步评估。P4 为规范问题，不阻塞。
