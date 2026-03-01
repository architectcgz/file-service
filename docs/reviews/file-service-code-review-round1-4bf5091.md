# file-service 代码 Review（第 1 轮）：使用原子 SQL 替代先读后写，消除秒传竞态条件

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit `4bf5091`，6 个文件，65 行新增 / 29 行删除 |
| 变更概述 | 新增 `findByHashAndIncrementCount` 原子 SQL，将秒传场景的「SELECT + UPDATE」两步操作合并为一条 UPDATE，消除并发竞态窗口 |
| 审查基准 | `docs/architecture/core-workflows.md` |
| 审查日期 | 2026-02-28 |

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `StorageObjectRepository.java` | 新增 `findByHashAndIncrementCount` 接口方法 |
| `StorageObjectRepositoryImpl.java` | 实现 `findByHashAndIncrementCount` |
| `StorageObjectMapper.java` | 新增原子 UPDATE SQL |
| `UploadApplicationService.java` | `uploadImage`/`uploadFile` 秒传逻辑改用原子操作 |
| `PresignedUrlService.java` | 预签名确认流程秒传逻辑改用原子操作 |
| `InstantUploadService.java` | 秒传服务改用原子操作 |

## 问题列表

### P1-高：原子 UPDATE 后紧跟 SELECT 存在竞态窗口，秒传可能读到脏数据或被删除的记录

| 字段 | 内容 |
|------|------|
| 问题级别 | 高 |
| 问题描述 | `findByHashAndIncrementCount` 原子增加引用计数后，紧接着调用 `findByFileHash` 查询 StorageObject 信息。这两步之间没有事务保护，并发场景下另一个线程可能在 UPDATE 和 SELECT 之间执行了删除操作（引用计数归零 → deleteById），导致 SELECT 查不到记录抛出「秒传命中但存储对象查询失败」异常。更严重的是：引用计数已经 +1 但没有对应的 FileRecord，造成引用计数泄漏 |
| 影响范围 | 4 处调用点全部受影响：`UploadApplicationService.uploadImage`、`uploadFile`、`PresignedUrlService`、`InstantUploadService` |
| 修正建议 | 将原子 UPDATE 改为「UPDATE ... RETURNING *」（PostgreSQL）或改用单条 SQL 同时完成 UPDATE + SELECT（如 MyBatis `@Select` + `FOR UPDATE`）；或者把 `findByHashAndIncrementCount` + `findByFileHash` 放在同一个事务内，并对 StorageObject 行加锁 |
| 可选方案 | 将 `findByHashAndIncrementCount` 改为返回 `Optional<StorageObject>`（在 Mapper 层用 `SELECT ... FOR UPDATE` 加锁后 UPDATE，再返回完整对象），一次调用解决问题 |

### P2-中：秒传失败时引用计数已增加但无回滚

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `findByHashAndIncrementCount` 成功后，后续任何步骤失败（如 S3 缩略图上传失败、`saveImageUploadRecord` 事务回滚），引用计数已经 +1 但不会回滚，导致 StorageObject 永远无法被正确清理 |
| 影响范围 | `uploadImage` 秒传分支（缩略图上传可能失败）、所有秒传分支的数据库写入失败场景 |
| 修正建议 | 在秒传分支的异常处理中增加 `decrementReferenceCount` 补偿，与非秒传分支的 `cleanupS3Quietly` 补偿对称 |
| 可选方案 | 无 |

### P3-低：SQL 缺少 `LIMIT 1`

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `findByHashAndIncrementCount` 的 UPDATE 按 `app_id + file_hash` 匹配，如果存在多条相同 hash 的记录（理论上不应该，但没有唯一约束保证），会同时更新多行 |
| 影响范围 | 数据异常场景下可能多行引用计数同时 +1 |
| 修正建议 | 确认 `(app_id, file_hash)` 上有唯一索引；如果没有，SQL 加 `LIMIT 1` 防御 |
| 可选方案 | 无 |

## 问题统计

| 级别 | 数量 | 编号 |
|------|------|------|
| 高 | 1 | P1 |
| 中 | 1 | P2 |
| 低 | 1 | P3 |
| 合计 | 3 | — |

## 审查结论

原子化思路正确，用单条 UPDATE 替代 SELECT + UPDATE 消除了引用计数的竞态窗口。但引入了新的问题：原子 UPDATE 和后续 SELECT 之间仍然存在无事务保护的间隙（P1），且秒传失败时引用计数无补偿回滚（P2）。

P1 是阻塞项，建议将 `findByHashAndIncrementCount` 改为返回完整 StorageObject 对象（SELECT FOR UPDATE + UPDATE 在同一事务内），一次调用同时解决 P1 和查询需求。P2 建议同步修复。

修复 P1、P2 后再合并。
