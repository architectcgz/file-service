# Requirements Document

## Overview

本需求文档定义了在 file-service 中实现文件 URL 缓存功能的需求。根据架构决策，文件 URL 的缓存应当在 file-service 内部实现，而不是在调用方服务（blog-upload、blog-post 等）中实现。这样可以避免代码重复、资源浪费，并确保缓存一致性。

**背景**：
- file-service 是文件数据的唯一真实来源（Single Source of Truth）
- 多个服务（blog-post、blog-user、blog-comment 等）都需要查询文件 URL
- 在调用方实现缓存会导致重复代码和资源浪费
- file-service 最适合管理文件相关的缓存

**目标**：
- 在 file-service 的 `FileAccessService.getFileUrl()` 方法中实现缓存
- 使用 Cache-Aside 模式
- 在文件删除时清除缓存
- 提供可配置的 TTL
- 对调用方透明（无需修改调用方代码）

## Functional Requirements

### FR1: Redis 缓存集成

**EARS Pattern**: WHEN file-service 启动时，THEN 系统应当成功连接到 Redis 服务器。

**Acceptance Criteria**:
- [ ] file-service 的 pom.xml 包含 spring-boot-starter-data-redis 依赖
- [ ] application.yml 包含 Redis 连接配置
- [ ] 应用启动时能够成功连接到 Redis
- [ ] 连接失败时有明确的错误日志

### FR2: 文件 URL 缓存读取

**EARS Pattern**: WHEN 调用 `getFileUrl(appId, fileId, userId)` 方法时，IF 缓存中存在该 fileId 的 URL，THEN 系统应当直接返回缓存的 URL，而不查询数据库。

**Acceptance Criteria**:
- [ ] 首次查询时从数据库获取 URL
- [ ] 后续查询时从 Redis 缓存获取 URL
- [ ] 缓存命中时记录 DEBUG 级别日志
- [ ] 缓存未命中时记录 DEBUG 级别日志
- [ ] 缓存命中率 > 80%（监控指标）

### FR3: 文件 URL 缓存写入

**EARS Pattern**: WHEN 从数据库查询到文件 URL 后，THEN 系统应当将 URL 写入 Redis 缓存，并设置过期时间。

**Acceptance Criteria**:
- [ ] 数据库查询成功后立即写入缓存
- [ ] 缓存 Key 格式为 `file:{fileId}:url`
- [ ] 缓存值为文件的访问 URL（字符串）
- [ ] 设置 TTL 为 1 小时（可配置）
- [ ] 写入失败不影响正常业务流程

### FR4: 文件删除时清除缓存

**EARS Pattern**: WHEN 文件被删除时，THEN 系统应当立即清除该文件的 URL 缓存。

**Acceptance Criteria**:
- [ ] 文件删除成功后立即删除缓存
- [ ] 使用与查询相同的缓存 Key 格式
- [ ] 记录缓存清除操作的日志
- [ ] 缓存删除失败不影响文件删除操作

### FR5: Redis Key 常量管理

**EARS Pattern**: WHEN 需要生成 Redis 缓存 Key 时，THEN 系统应当使用专门的 `FileRedisKeys` 类来生成 Key，而不是硬编码字符串。

**Acceptance Criteria**:
- [ ] 创建 `FileRedisKeys` 类在 `infrastructure/cache` 包下
- [ ] 提供 `fileUrl(String fileId)` 静态方法
- [ ] Key 格式为 `file:{fileId}:url`
- [ ] 所有缓存操作都使用该类生成 Key
- [ ] 符合常量管理规范（03-constants-config.md）

### FR6: 缓存配置化

**EARS Pattern**: WHEN 需要调整缓存行为时，THEN 系统应当支持通过配置文件修改缓存参数，而不需要修改代码。

**Acceptance Criteria**:
- [ ] TTL 可通过 `file-service.cache.url.ttl` 配置（默认 3600 秒）
- [ ] 缓存开关可通过 `file-service.cache.enabled` 配置（默认 true）
- [ ] Redis 连接参数可通过环境变量覆盖
- [ ] 配置变更后重启生效

## Non-Functional Requirements

### NFR1: 性能要求

**EARS Pattern**: WHEN 缓存命中时，THEN 文件 URL 查询的响应时间应当 < 10ms。

**Acceptance Criteria**:
- [ ] 缓存命中时响应时间 P99 < 10ms
- [ ] 缓存未命中时响应时间 P99 < 50ms
- [ ] 缓存命中率 > 80%

### NFR2: 可用性要求

**EARS Pattern**: WHEN Redis 不可用时，THEN 系统应当降级到直接查询数据库，而不是返回错误。

**Acceptance Criteria**:
- [ ] Redis 连接失败时记录 WARN 日志
- [ ] 降级到数据库查询
- [ ] 不影响正常业务流程
- [ ] 提供降级次数监控指标

### NFR3: 可观测性要求

**EARS Pattern**: WHEN 缓存操作发生时，THEN 系统应当记录详细的日志和监控指标。

**Acceptance Criteria**:
- [ ] 记录缓存命中/未命中日志（DEBUG 级别）
- [ ] 记录缓存写入/删除日志（DEBUG 级别）
- [ ] 提供缓存命中率监控指标
- [ ] 提供缓存操作耗时监控指标
- [ ] 日志包含 fileId、操作类型、结果

### NFR4: 安全性要求

**EARS Pattern**: WHEN 记录日志时，THEN 系统不应当记录完整的文件 URL（可能包含敏感信息）。

**Acceptance Criteria**:
- [ ] 日志中只记录 fileId，不记录完整 URL
- [ ] 日志中不记录 userId 等敏感信息
- [ ] 符合安全规范（12-security.md）

## Constraints

### Technical Constraints

- 必须使用 Spring Boot 3.x 和 Spring Data Redis
- 必须兼容现有的 file-service 架构
- 不能修改 `FileAccessService` 的公共接口
- 必须支持 Redis Standalone 和 Redis Cluster

### Business Constraints

- 缓存实现必须对调用方透明
- 不能影响现有功能的正常运行
- 必须在 2 个工作日内完成实现

## Success Criteria

1. **功能完整性**：所有 Functional Requirements 的 Acceptance Criteria 都通过
2. **性能达标**：缓存命中率 > 80%，响应时间 P99 < 10ms
3. **测试覆盖**：单元测试覆盖率 > 80%，集成测试通过
4. **文档完整**：代码注释完整，符合注释规范（02-code-standards.md）
5. **监控就绪**：提供缓存命中率、响应时间等监控指标

## Out of Scope

以下内容不在本次需求范围内：

- 多级缓存（本地缓存 + Redis）
- 缓存预热功能
- 缓存穿透/击穿/雪崩防护（当前场景不需要）
- 文件详情（FileDetail）的缓存（只缓存 URL）
- 调用方服务的缓存实现（已明确在 file-service 实现）

## Dependencies

- Redis 服务器（端口 6379，已在 docker-compose 中配置）
- Spring Boot 3.2.4
- Spring Data Redis
- 现有的 FileAccessService 和 FileRecordRepository

## Risks and Mitigations

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Redis 不可用导致服务中断 | 高 | 低 | 实现降级逻辑，Redis 不可用时查询数据库 |
| 缓存一致性问题 | 中 | 中 | 文件删除时立即清除缓存 |
| 缓存 Key 冲突 | 低 | 低 | 使用明确的 Key 前缀 `file:{fileId}:url` |
| 性能不达标 | 中 | 低 | 使用 Redis Pipeline 批量操作 |

## Related Documents

- [缓存架构决策文档](../file-service-fileid-migration/caching-architecture.md)
- [缓存规范](../../../blog-microservice/.kiro/steering/common/16-cache.md)
- [常量管理规范](../../../blog-microservice/.kiro/steering/common/03-constants-config.md)
- [fileId 迁移设计文档](../file-service-fileid-migration/design.md)

## Approval

- [ ] 产品负责人审批
- [ ] 技术负责人审批
- [ ] 架构师审批

---

**文档版本**: 1.0  
**创建日期**: 2026-02-09  
**最后更新**: 2026-02-09  
**作者**: 开发团队
