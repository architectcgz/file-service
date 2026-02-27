# Requirements Document

## Introduction

本文档定义了分片上传 Bitmap 优化功能的需求。当前分片上传系统将每个分片记录都写入 PostgreSQL 数据库，导致大文件上传时产生大量数据库写入操作。通过引入 Redis Bitmap 作为快速缓存层，并配合定期同步和故障回退机制，可以显著提升分片上传的性能和用户体验。

## Glossary

- **Upload_Task**: 分片上传任务，包含文件元信息和上传状态
- **Upload_Part**: 单个分片记录，包含分片编号、ETag 等信息
- **Bitmap**: Redis 位图数据结构，用于高效记录分片上传状态
- **Part_Number**: 分片编号，从 1 开始的整数
- **Sync_Strategy**: 同步策略，定义何时将 Bitmap 数据同步到数据库
- **Fallback_Mechanism**: 回退机制，Redis 不可用时自动切换到数据库
- **Repository**: 仓储层，负责数据持久化和查询

## Requirements

### Requirement 1: Redis Bitmap 分片状态记录

**User Story:** 作为系统开发者，我希望使用 Redis Bitmap 记录分片上传状态，以便减少数据库写入压力并提升上传性能。

#### Acceptance Criteria

1. WHEN 客户端上传一个分片 THEN THE System SHALL 将分片状态记录到 Redis Bitmap 中
2. WHEN 记录分片状态 THEN THE System SHALL 使用 taskId 作为 Bitmap key 的一部分
3. WHEN 记录分片状态 THEN THE System SHALL 使用 partNumber - 1 作为 Bitmap 的位偏移量
4. WHEN 创建 Bitmap key THEN THE System SHALL 设置 24 小时的过期时间
5. WHEN Bitmap 写入成功 THEN THE System SHALL 返回成功响应

### Requirement 2: 分片状态查询

**User Story:** 作为系统开发者，我希望快速查询分片上传进度，以便向客户端返回实时的上传状态。

#### Acceptance Criteria

1. WHEN 查询已完成分片数量 THEN THE System SHALL 优先从 Redis Bitmap 使用 BITCOUNT 命令获取
2. WHEN 查询已完成分片列表 THEN THE System SHALL 遍历 Bitmap 返回所有值为 1 的分片编号
3. WHEN 查询上传进度 THEN THE System SHALL 计算已完成分片数量与总分片数的比例
4. WHEN Bitmap 查询返回结果 THEN THE System SHALL 在 10 毫秒内完成响应

### Requirement 3: 定期同步到数据库

**User Story:** 作为系统管理员，我希望分片记录定期同步到数据库，以便在 Redis 故障时不丢失数据。

#### Acceptance Criteria

1. WHEN 上传分片数量达到配置的批次大小 THEN THE System SHALL 触发异步同步到数据库
2. WHEN 执行定期同步 THEN THE System SHALL 比较 Bitmap 和数据库中的分片记录找出差异
3. WHEN 发现新分片 THEN THE System SHALL 批量插入到数据库
4. WHEN 同步失败 THEN THE System SHALL 记录错误日志但不影响上传流程
5. WHERE 配置了同步批次大小 THEN THE System SHALL 按配置的批次大小触发同步

### Requirement 4: 上传完成时全量同步

**User Story:** 作为系统开发者，我希望在上传完成时将所有分片记录同步到数据库，以便确保数据完整性和可追溯性。

#### Acceptance Criteria

1. WHEN 所有分片上传完成 THEN THE System SHALL 将 Bitmap 中的所有分片记录同步到数据库
2. WHEN 执行全量同步 THEN THE System SHALL 使用批量插入操作
3. WHEN 全量同步成功 THEN THE System SHALL 删除 Redis Bitmap 释放内存
4. WHEN 全量同步失败 THEN THE System SHALL 抛出异常并保留 Bitmap 数据
5. WHEN 同步到数据库 THEN THE System SHALL 记录每个分片的 partNumber、etag 和 uploadedAt

### Requirement 5: Redis 故障回退机制

**User Story:** 作为系统管理员，我希望在 Redis 不可用时系统能自动回退到数据库，以便保证服务的高可用性。

#### Acceptance Criteria

1. WHEN Redis 连接失败 THEN THE System SHALL 自动切换到数据库模式
2. WHEN 使用数据库模式 THEN THE System SHALL 直接将分片记录写入 PostgreSQL
3. WHEN Redis 恢复可用 THEN THE System SHALL 自动切换回 Bitmap 模式
4. WHEN 发生回退 THEN THE System SHALL 记录警告日志包含错误原因
5. WHEN 回退到数据库 THEN THE System SHALL 保证功能正常不影响用户上传

### Requirement 6: 配置化管理

**User Story:** 作为系统管理员，我希望通过配置文件控制 Bitmap 优化功能，以便根据实际情况灵活调整。

#### Acceptance Criteria

1. THE System SHALL 提供配置项控制 Bitmap 功能的启用或禁用
2. THE System SHALL 提供配置项设置同步批次大小
3. THE System SHALL 提供配置项设置 Bitmap 过期时间
4. WHEN Bitmap 功能禁用 THEN THE System SHALL 完全使用数据库模式
5. WHEN 修改配置 THEN THE System SHALL 在应用重启后生效

### Requirement 7: 监控和可观测性

**User Story:** 作为系统运维人员，我希望监控 Bitmap 优化的效果，以便评估性能提升和发现潜在问题。

#### Acceptance Criteria

1. WHEN 记录分片状态 THEN THE System SHALL 记录 DEBUG 级别日志包含 taskId 和 partNumber
2. WHEN Redis 操作失败 THEN THE System SHALL 记录 WARN 级别日志包含错误堆栈
3. WHEN 执行同步操作 THEN THE System SHALL 记录 INFO 级别日志包含同步的分片数量
4. WHEN 发生回退 THEN THE System SHALL 增加回退计数器指标
5. THE System SHALL 提供 Bitmap 命中率的监控指标

### Requirement 8: 数据一致性保证

**User Story:** 作为系统开发者，我希望确保 Bitmap 和数据库之间的数据一致性，以便避免数据丢失或不一致。

#### Acceptance Criteria

1. WHEN 上传完成 THEN THE System SHALL 确保所有分片记录都已同步到数据库
2. WHEN 查询分片状态 THEN THE System SHALL 优先使用 Bitmap 但在 Bitmap 不存在时查询数据库
3. WHEN 断点续传 THEN THE System SHALL 能够从数据库恢复分片状态到 Bitmap
4. WHEN 系统重启 THEN THE System SHALL 能够从数据库加载未完成任务的分片状态
5. FOR ALL 已完成的上传任务 THE System SHALL 确保数据库中有完整的分片记录

### Requirement 9: 性能优化目标

**User Story:** 作为产品经理，我希望分片上传性能得到显著提升，以便改善用户体验和降低系统成本。

#### Acceptance Criteria

1. WHEN 上传 1000 个分片 THEN THE System SHALL 在 1 秒内完成所有分片状态记录
2. WHEN 查询上传进度 THEN THE System SHALL 在 10 毫秒内返回结果
3. WHEN 使用 Bitmap 优化 THEN THE System SHALL 减少数据库写入操作至少 90%
4. WHEN 存储分片状态 THEN THE System SHALL 使用的内存不超过 2KB per 1000 分片
5. WHEN 并发上传 100 个文件 THEN THE System SHALL 保持响应时间在 100 毫秒以内


