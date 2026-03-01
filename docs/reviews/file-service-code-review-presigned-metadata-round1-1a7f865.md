# file-service 代码 Review（presigned-metadata 第 1 轮）：预签名上传确认时通过 HeadObject 获取真实文件元数据

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | presigned-metadata |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | master..fix-presigned-metadata（1 commit, 1a7f865），5 文件，+116/-18 行 |
| 变更概述 | confirmUpload() 中硬编码的 fileSize(0L) 和 contentType("application/octet-stream") 替换为通过 HeadObject 获取的真实值 |
| 审查基准 | docs/todo.md 问题 8（PresignedUrlService.confirmUpload() 元数据未实现） |
| 审查日期 | 2026-03-01 |

## 问题清单

### 🔴 高优先级

（无）

### 🟡 中优先级

#### [M1] `confirmUpload()` 移除了 `exists()` 校验但未等价替代

- **文件**：`PresignedUrlService.java` 第 127-132 行
- **问题描述**：原代码先调用 `storageService.exists()` 校验文件是否存在于 S3，不存在则抛出明确的 "文件不存在，请先上传文件" 异常。修改后直接调用 `getObjectMetadata()`，文件不存在时由 `S3StorageService.getObjectMetadata()` 抛出通用的 `BusinessException("文件不存在: " + path)`。
- **影响范围/风险**：错误消息语义变化，用户体验略有退化。功能上等价。
- **修正建议**：在 `getObjectMetadata()` 的 `NoSuchKeyException` 处理中使用更明确的错误消息，或在 `confirmUpload()` 中 catch 后重新包装。

### 🟢 低优先级

#### [L1] `ObjectMetadata` 命名与 AWS SDK 的同名类容易混淆

- **文件**：`infrastructure/storage/ObjectMetadata.java`
- **问题描述**：AWS SDK v1 中有同名类 `com.amazonaws.services.s3.model.ObjectMetadata`，虽然本项目用 SDK v2，但类名相同容易在 import 时混淆。
- **修正建议**：考虑重命名为 `StorageObjectMetadata`。非阻塞性问题。

## 结论

**可合并**。实现完整且正确，M1 是错误消息语义的小问题，不影响功能正确性。
