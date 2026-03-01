# file-service 代码 Review（access-control 第 2 轮）：补全访问控制检查链，统一收敛校验逻辑

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | access-control |
| 轮次 | 第 2 轮（修复后复审） |
| 审查范围 | master..fix-access-control（2 commits, c4c3681），4 文件，+235/-69 行 |
| 变更概述 | 统一收敛访问控制到 checkFileAccess()，完整检查链（租户隔离→文件状态→访问级别），缓存命中后增加权限校验，补充测试 |
| 审查基准 | docs/todo.md 问题 9（访问控制检查不完整） |
| 审查日期 | 2026-03-01 |
| 上轮问题数 | 9 项（3 高 / 4 中 / 2 低） |

## 问题清单

### 🔴 高优先级

（无）

### 🟡 中优先级

#### [M1] 缓存命中时额外查询数据库，削弱了缓存收益

- **文件**：`FileAccessService.java` 第 57-71 行
- **问题描述**：`getFileUrl()` 中缓存命中后，仍然执行 `fileRecordRepository.findById(fileId)` 查询数据库做权限校验。缓存只省掉了 S3 URL 生成，没有省掉数据库查询。
- **影响范围/风险**：高并发场景下公开文件的 URL 请求仍然会打到数据库。
- **修正建议**：缓存中同时存储 appId 和 status 信息，命中时直接校验不查库；或缓存 key 中包含 appId。

#### [M2] `checkFileAccess()` 对公开文件所有者以外的用户也放行，语义不够清晰

- **文件**：`FileAccessService.java` `updateAccessLevel()` 方法
- **问题描述**：`checkFileAccess()` 对 PUBLIC 文件直接 return，但 `updateAccessLevel()` 调用后还需单独检查所有者。方法名暗示"通过即有权操作"，实际修改操作还需额外校验。
- **影响范围/风险**：代码可读性问题，后续开发者容易误解。
- **修正建议**：在方法注释中明确说明"此方法仅校验读取权限"，或拆分为 `checkReadAccess()` / `checkWriteAccess()`。

### 🟢 低优先级

#### [L1] `FileErrorMessages` 常量类只覆盖了 FileAccessService 的错误消息

- **文件**：`common/constant/FileErrorMessages.java`
- **问题描述**：该类只定义了 3 个常量，仅覆盖 FileAccessService 场景。与 fix-error-messages-v2 分支（问题 11）可能存在重复或冲突。
- **影响范围/风险**：合并时需统一处理，当前不阻塞。
- **修正建议**：与 fix-error-messages-v2 分支合并时统一。

## 结论

**可合并**。实现质量高，上轮高优问题（缓存绕过、跨租户信息泄漏）均已修复。M1 缓存收益问题建议后续优化，不阻塞合并。
