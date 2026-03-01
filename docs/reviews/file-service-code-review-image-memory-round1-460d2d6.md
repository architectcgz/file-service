# file-service 代码 Review（image-memory 第 1 轮）：图片上传使用临时文件降低内存峰值

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | image-memory |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | master..fix-image-memory（1 commit, 460d2d6），7 文件，+293/-58 行 |
| 变更概述 | uploadImage() 从 file.getBytes() 全量内存模式改为临时文件模式，ImageProcessor/StorageService 补齐基于文件的方法 |
| 审查基准 | docs/todo.md 问题 7（图片上传内存峰值过高） |
| 审查日期 | 2026-03-01 |

## 问题清单

### 🔴 高优先级

#### [H1] `@Transactional` 仍然包裹整个 `uploadImage()` 方法

- **文件**：`UploadApplicationService.java` 第 62 行
- **问题描述**：方法上仍保留 `@Transactional(rollbackFor = Exception.class)`，而方法内部包含临时文件写入、图片处理、S3 上传等大量 I/O 操作。这正是 todo.md 问题 1（已在其他分支修复）要解决的长事务问题。
- **影响范围/风险**：与问题 1 的修复方案冲突。合并顺序不当会覆盖事务拆分的改动。
- **修正建议**：与问题 1 的分支协调合并顺序，或在本分支中同步移除 `@Transactional`，将数据库写入拆分为独立的短事务方法。

### 🟡 中优先级

#### [M1] `processToFile()` 同一张图片被解码两次

- **文件**：`ImageProcessor.java` `processToFile()` 方法
- **问题描述**：先 `ImageIO.read(source)` 读取 BufferedImage 获取宽高，然后置 null，再由 Thumbnailator 重新读取同一文件。
- **影响范围/风险**：大图片两次解码增加 CPU 开销和临时内存占用。
- **修正建议**：使用 `ImageReader` 只读取图片元数据（尺寸），不解码像素数据。

#### [M2] `generateThumbnailToFile()` 中 outputQuality 硬编码为 0.8

- **文件**：`ImageProcessor.java` `generateThumbnailToFile()` 方法
- **问题描述**：`.outputQuality(0.8)` 硬编码，未通过配置注入。与全局 CLAUDE.md「禁止硬编码」规范冲突，也与 `processToFile()` 中使用 `config.getQuality()` 的做法不一致。
- **修正建议**：从 `ImageProcessingProperties` 中读取缩略图质量配置。

### 🟢 低优先级

#### [L1] 临时文件创建在系统默认 temp 目录，未配置独立路径

- **文件**：`UploadApplicationService.java` 第 72、100、103 行
- **问题描述**：`Files.createTempFile(prefix, ".tmp")` 使用系统默认临时目录。容器化部署中可能空间有限。
- **修正建议**：在 `ImageProcessingProperties` 中增加 `tempDir` 配置项。

## 结论

**需修复后合并**。H1 是合并顺序/事务冲突问题，需与问题 1 的分支协调。M2 硬编码需修复。
