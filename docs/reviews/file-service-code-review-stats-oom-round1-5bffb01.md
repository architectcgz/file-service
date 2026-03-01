# file-service 代码 Review（stats-oom 第 1 轮）：统计查询下推 SQL 聚合

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | stats-oom |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | master..fix-stats-oom（1 commit, 5bffb01），8 文件，+197/-40 行 |
| 变更概述 | 将 getStorageStatistics() 全量加载 + Java Stream 聚合改为 3 条 SQL 聚合查询 |
| 审查基准 | docs/todo.md 问题 6（统计查询全量加载内存 OOM 风险） |
| 审查日期 | 2026-03-01 |

## 问题清单

### 🔴 高优先级

#### [H1] SQL 聚合缺少 `WHERE status != 'DELETED'` 过滤

- **文件**：`FileRecordMapper.xml` 第 80-109 行
- **问题描述**：todo.md 修复方案明确要求 `WHERE status != 'DELETED'`，但 3 条聚合 SQL（`selectStorageStatistics`、`selectFileCountByContentType`、`selectStorageByTenant`）均未加此条件。已删除文件会被计入统计。
- **影响范围/风险**：统计数据不准确——总文件数、总存储空间、按类型/租户分布均包含已删除文件。
- **修正建议**：3 条 SQL 均加 `WHERE status != 'DELETED'`。

### 🟡 中优先级

#### [M1] 聚合查询缺少 appId 过滤参数

- **文件**：`FileRecordMapper.xml` 第 80-109 行
- **问题描述**：todo.md 方案中 `selectStorageStatistics` 和 `selectFileCountByContentType` 均包含 `<if test="appId != null">AND app_id = #{appId}</if>` 条件，支持按租户过滤。但实际实现的 3 条 SQL 都是全局聚合，不接受 appId 参数。
- **影响范围/风险**：无法按单个租户查询统计数据。
- **修正建议**：至少在 `selectStorageStatistics` 和 `selectFileCountByContentType` 中增加可选的 appId 过滤。

### 🟢 低优先级

#### [L1] DTO 放在 application.dto 包中，但被 domain.repository 接口引用

- **文件**：`FileRecordRepository.java` 第 4-6 行
- **问题描述**：`StorageStatisticsAggregation`、`ContentTypeCount`、`TenantStorageAggregation` 定义在 `application.dto` 包，但 `domain.repository.FileRecordRepository` 接口直接引用了它们。domain 层不应依赖 application 层。
- **影响范围/风险**：分层依赖方向违规，不影响运行但破坏架构一致性。
- **修正建议**：将这 3 个 DTO 移到 `domain.model` 或 `domain.repository` 包下。

## 结论

**需修复后合并**。H1 是数据正确性问题，必须修复。M1 建议补齐以与方案保持一致。
