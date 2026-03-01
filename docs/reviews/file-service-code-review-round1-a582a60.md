# file-service 代码 Review（第 1 轮）：将 S3 操作移到事务外，避免长事务占用数据库连接

## Review 信息

| 字段 | 说明 |
|------|------|
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit `a582a60`，3 个文件，252 行新增 / 255 行删除 |
| 变更概述 | 将 `DirectUploadService`、`MultipartUploadService`、`UploadApplicationService` 中的 S3 耗时操作移到 `@Transactional` 外，缩短事务持有数据库连接的时间；上传场景增加 S3 补偿清理逻辑 |
| 审查基准 | `docs/architecture/core-workflows.md`、`docs/architecture/layered-architecture.md` |
| 审查日期 | 2026-02-28 |

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `DirectUploadService.java` | 拆分 `completeDirectUpload` 为外层方法 + `saveDirectUploadRecord` 短事务 |
| `MultipartUploadService.java` | 拆分 `completeUpload` 为外层方法 + `saveMultipartUploadRecord` 短事务 |
| `UploadApplicationService.java` | 拆分 `uploadImage`/`uploadFile`/`deleteFile` 为外层方法 + 短事务方法；新增 `cleanupS3Quietly` 补偿 |

## 问题列表

### P1-高：Spring 代理失效 — 同类内部调用 `@Transactional` 不生效

| 字段 | 内容 |
|------|------|
| 问题级别 | 高 |
| 问题描述 | `DirectUploadService.completeDirectUpload()` 直接调用 `this.saveDirectUploadRecord()`，`MultipartUploadService`、`UploadApplicationService` 同理。Spring AOP 基于代理，同类内部方法调用不经过代理对象，`@Transactional` 注解不会生效，等于裸写库没有事务保护 |
| 影响范围 | 所有 3 个文件中新拆出的 5 个 `@Transactional protected` 方法全部受影响。本次改动的核心目标（短事务）实际上完全没有生效 |
| 修正建议 | 将 `save*` / `deleteFileInTransaction` 方法抽到独立的 `@Service` 类（如 `FileTransactionService`），由外层 Service 注入调用，确保走 Spring 代理 |
| 可选方案 | ① 注入自身代理 `@Lazy private DirectUploadService self;` 然后 `self.saveDirectUploadRecord()`；② 改用 `TransactionTemplate` 编程式事务 |

### P2-高：`deleteFile` 中 StorageObject 记录删除不在事务内，存在并发窗口

| 字段 | 内容 |
|------|------|
| 问题级别 | 高 |
| 问题描述 | `deleteFileInTransaction` 事务内执行了 `decrementReferenceCount` 并判断 `canBeDeleted`，但事务提交后，外层又通过 `storageObjectRepository.findById()` 重新查询并执行 `deleteById()`。这两步不在事务保护内 |
| 影响范围 | 并发删除场景下，事务提交后、`deleteById` 执行前，另一个请求可能读到引用计数已归零但尚未删除的 StorageObject，导致重复删除 S3 或数据不一致 |
| 修正建议 | 在 `deleteFileInTransaction` 事务内，当 `canBeDeleted` 为 true 时直接执行 `storageObjectRepository.deleteById()`，仅将 S3 物理删除放在事务外 |
| 可选方案 | 无 |

### P3-中：Javadoc 注释重复（旧注释未删除）

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `MultipartUploadService:202-212` 和 `DirectUploadService:300-310` 出现两段连续的 Javadoc 注释，旧的 `@param/@return` 风格注释没有删除，新的注释直接追加在后面 |
| 影响范围 | 编译不报错，但阅读混乱，IDE 生成的文档也会异常 |
| 修正建议 | 删除旧的 Javadoc 块，只保留新的注释 |
| 可选方案 | 无 |

### P4-中：`completeUpload` / `completeDirectUpload` 缺少 S3 失败后的补偿清理

| 字段 | 内容 |
|------|------|
| 问题级别 | 中 |
| 问题描述 | `uploadImage` / `uploadFile` 中 S3 上传成功但数据库写入失败时，有 `cleanupS3Quietly` 补偿。但 `completeUpload` 和 `completeDirectUpload` 中 S3 `completeMultipartUpload` 成功后如果 `save*Record` 抛异常，已合并的 S3 文件没有任何补偿清理 |
| 影响范围 | S3 上产生孤立文件，数据库无对应记录，需人工清理 |
| 修正建议 | 参照 `uploadImage` 的模式，在调用 `save*Record` 外层加 try-catch，失败时调用 `storageService.delete(task.getStoragePath())` 补偿 |
| 可选方案 | 无 |

### P5-低：`completedPartNumbers` 查询后未使用

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `MultipartUploadService:234` 调用 `uploadPartRepository.findCompletedPartNumbers(taskId)` 赋值给 `completedPartNumbers`，但后续没有任何地方使用该变量 |
| 影响范围 | 无效数据库查询，浪费资源 |
| 修正建议 | 删除该行 |
| 可选方案 | 无 |

### P6-低：Builder 链式调用可读性下降

| 字段 | 内容 |
|------|------|
| 问题级别 | 低 |
| 问题描述 | `saveFileUploadRecord` 中 `FileRecord.builder()` 和 `UploadResult.builder()` 将多个 `.field()` 挤在同一行（如 `.id(generateFileId()).appId(appId).userId(userId)...`），相比原来每行一个字段的风格可读性明显下降 |
| 影响范围 | 代码可读性、后续维护 |
| 修正建议 | 保持每行一个字段的 Builder 风格，与项目其他代码一致 |
| 可选方案 | 无 |

## 问题统计

| 级别 | 数量 | 编号 |
|------|------|------|
| 高 | 2 | P1、P2 |
| 中 | 2 | P3、P4 |
| 低 | 2 | P5、P6 |
| 合计 | 6 | — |

## 审查结论

本次改动方向正确，将 S3 耗时操作移到事务外以缩短数据库连接持有时间。但存在 2 个高级别阻塞问题：

1. P1（Spring 代理失效）直接导致本次改动的核心目标未生效 — 所有新拆出的 `@Transactional` 方法因同类内部调用不走代理，事务注解形同虚设
2. P2（StorageObject 删除并发窗口）在高并发删除场景下可能导致数据不一致

建议修复 P1、P2 后再合并，P3-P6 可在同一轮修复中一并处理。

推荐修复方案：新建 `FileTransactionHelper`（或类似命名的 `@Service`），将所有短事务方法集中到该类，由 `DirectUploadService`、`MultipartUploadService`、`UploadApplicationService` 注入调用，一次性解决代理失效问题。
