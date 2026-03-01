# file-service 代码 Review（cache-gap 第 2 轮）：缓存一致性缺口修复

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | cache-gap |
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | master..fix-cache-gap（2 commits, 342d100），6 文件，+380/-776 行 |
| 变更概述 | 抽取 FileUrlCacheManager 统一缓存操作，updateAccessLevel() 事务提交后清除缓存，测试适配 |
| 审查基准 | docs/todo.md 问题 5（缓存一致性缺口） |
| 审查日期 | 2026-03-01 |
| 上轮问题数 | 5 项（3 高 / 4 中 / 2 低）（注：round1 中 H1 缓存清除逻辑重复已通过抽取 FileUrlCacheManager 解决） |

## 问题清单

### 🔴 高优先级

（无）

### 🟡 中优先级

#### [M1] deleteFile() 路径未清除缓存

- **文件**：`FileAccessService.java` 整体
- **问题描述**：todo.md 明确要求「在所有修改文件状态的操作中清除缓存」，并特别提到「同样在 deleteFile() 中也要清除（确认是否已有）」。`FileAccessService` 本身没有 `deleteFile()` 方法，删除操作在 `UploadApplicationService.deleteFile()` 中。当前 fix-cache-gap 分支只在 `FileManagementService.deleteFile()` 中调用了 `fileUrlCacheManager.evict(fileId)`（管理员删除路径），但 `UploadApplicationService.deleteFile()`（用户删除路径）未注入 `FileUrlCacheManager`，也未清除缓存。
- **影响范围/风险**：用户删除公开文件后，缓存中的 URL 在 TTL 内仍可访问，已删除文件的 URL 继续对外暴露。
- **修正建议**：在 `UploadApplicationService.deleteFile()` 中也注入 `FileUrlCacheManager` 并在删除成功后调用 `evict(fileId)`。

#### [M2] updateAccessLevel() 的 @Transactional 是同类内部方法，需确认事务代理生效

- **文件**：`FileAccessService.java` 第 196 行
- **问题描述**：`updateAccessLevel()` 标注了 `@Transactional`，并使用 `TransactionSynchronizationManager.registerSynchronization()` 在事务提交后清除缓存。这要求该方法必须通过 Spring 代理调用才能生效。当前 `updateAccessLevel()` 是 `FileAccessService` 的公共方法，由 Controller 层直接调用，代理是生效的。但如果未来有同类内部方法调用它，事务和 `afterCommit` 回调都不会触发。
- **影响范围/风险**：当前没有问题，但缺少防御性注释说明这一约束。
- **修正建议**：在方法注释中补充说明：

```java
/**
 * 更新文件访问级别
 * 注意：本方法依赖 Spring 事务代理，必须通过 Bean 注入调用，不可同类内部调用
 */
```

#### [M3] 缓存写入时机存在短暂不一致窗口

- **文件**：`FileAccessService.java` 第 90-94 行
- **问题描述**：`getFileUrl()` 中，查询数据库获取文件信息后，生成公开 URL 并写入缓存。如果在「数据库查询」和「缓存写入」之间，另一个请求执行了 `updateAccessLevel(PUBLIC -> PRIVATE)`，缓存中会写入一个已经过时的公开 URL。虽然 `updateAccessLevel` 的 `afterCommit` 会清除缓存，但存在极短的时间窗口：`updateAccessLevel` 事务提交 → `getFileUrl` 写入缓存 → `afterCommit` 清除缓存。如果 `getFileUrl` 的缓存写入恰好在 `afterCommit` 之后执行，旧 URL 会残留在缓存中。
- **影响范围/风险**：极端并发下的竞态条件，概率很低但理论上存在。缓存中残留的公开 URL 在 TTL 内可被访问。
- **修正建议**：这是经典的缓存双写竞态问题，完美解决需要引入版本号或延迟双删。考虑到当前场景（文件访问级别变更频率低），可以接受这个风险，但建议在代码注释中记录这个已知限制：

```java
// 已知限制：极端并发下 updateAccessLevel 与 getFileUrl 存在短暂竞态窗口
// 可通过延迟双删优化，当前评估风险可接受
fileUrlCacheManager.put(fileId, url);
```

### 🟢 低优先级

#### [L1] FileUrlCacheManager 中日志级别不统一

- **文件**：`FileUrlCacheManager.java` 第 42-46 行
- **问题描述**：`get()` 方法中 cache hit/miss 都用 `debug` 级别，`evict()` 中成功/未找到也用 `debug`，但 `put()` 中成功也用 `debug`。整体一致，但异常时 `get()` 用 `warn`，`put()` 用 `warn`，`evict()` 也用 `warn`——这部分是合理的。不过 `FileManagementService` 原来的 `clearCache()` 中成功清除用的是 `log.info`，现在降级为 `debug`，可能影响运维排查。
- **影响范围/风险**：缓存清除操作在生产环境中不可见，排查缓存问题时缺少线索。
- **修正建议**：`evict()` 成功时使用 `info` 级别，与业务操作的重要性匹配。

#### [L2] 测试代码大幅删减，覆盖率可能下降

- **文件**：`FileAccessServiceCacheTest.java`（-536/+265 行），`FileManagementServiceCacheTest.java`（-381/+部分行）
- **问题描述**：测试文件净减少约 400 行。抽取 `FileUrlCacheManager` 后，原来直接 mock `RedisTemplate` 的测试被简化为 mock `FileUrlCacheManager`。简化是合理的，但需要确认 `FileUrlCacheManager` 本身有独立的单元测试覆盖其内部逻辑（缓存开关、异常降级等）。
- **影响范围/风险**：如果 `FileUrlCacheManager` 缺少独立测试，缓存开关和异常降级逻辑就没有测试覆盖。
- **修正建议**：补充 `FileUrlCacheManagerTest`，覆盖以下场景：
  - `cacheProperties.isEnabled() == false` 时 get/put/evict 均为空操作
  - Redis 异常时 get 返回 null、put/evict 不抛异常

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 0 |
| 🟡 中 | 3 |
| 🟢 低 | 2 |
| 合计 | 5 |

## 总体评价

这是三个分支中质量最高的一个。核心修复到位——`updateAccessLevel()` 在事务提交后通过 `TransactionSynchronization.afterCommit()` 清除缓存，避免了事务回滚后缓存已被清除的窗口期问题。抽取 `FileUrlCacheManager` 统一管理缓存操作，消除了 `FileAccessService` 和 `FileManagementService` 中的重复缓存代码，异常降级处理完善。

主要遗漏是用户删除路径（`UploadApplicationService.deleteFile()`）未清除缓存，需要补充。其余为低风险的并发竞态和测试覆盖问题。

结论：**需修复后合并**（M1 用户删除路径缓存未清除需修复，其余可接受）。
