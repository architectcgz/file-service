# Implementation Plan: 分片上传 Bitmap 优化

## Overview

本实现计划将 Redis Bitmap 优化集成到现有的分片上传系统中。通过引入 Bitmap 作为快速缓存层，配合定期同步和故障回退机制，将数据库写入操作减少 90% 以上，同时保证数据可靠性和一致性。实现将采用渐进式方法，确保每个步骤都经过充分测试和验证。

## Tasks

- [x] 1. 配置和基础设施准备
  - 创建 BitmapProperties 配置类
  - 配置 AsyncConfig 异步线程池
  - 添加 Micrometer 依赖用于监控
  - 更新 application.yml 配置文件
  - _Requirements: 6.1, 6.2, 6.3_

- [x] 2. 实现 Redis Key 管理
  - [x] 2.1 创建 UploadRedisKeys 常量类
    - 定义 Bitmap key 格式和命名规范
    - 提供 key 生成方法
    - _Requirements: 1.2_

- [x] 3. 实现 UploadPartRepository 接口
  - [x] 3.1 定义 UploadPartRepository 接口
    - savePart() 方法
    - countCompletedParts() 方法
    - findCompletedPartNumbers() 方法
    - syncAllPartsToDatabase() 方法
    - loadPartsFromDatabase() 方法
    - _Requirements: 1.1, 2.1, 2.2, 4.1, 8.3_

- [x] 4. 实现 UploadPartRepositoryImpl 核心逻辑
  - [x] 4.1 实现 savePart() 方法
    - 检查 Bitmap 功能是否启用
    - 写入 Redis Bitmap (SETBIT)
    - 设置 TTL (24 小时)
    - 判断是否需要触发定期同步
    - Redis 失败时回退到数据库
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.5, 5.1, 5.2, 5.5_

  - [x] 4.2 编写 savePart() 属性测试

    - **Property 1: Bitmap 记录正确性**
    - **Validates: Requirements 1.1, 1.2, 1.3**

  - [x] 4.3 编写 savePart() 属性测试

    - **Property 2: Bitmap TTL 设置**    
    - **Validates: Requirements 1.4**

  - [ ]* 4.4 编写 savePart() 属性测试
    - **Property 12: Redis 故障自动回退**
    - **Validates: Requirements 5.1, 5.2, 5.5**

  - [x] 4.5 实现 countCompletedParts() 方法
    - 优先从 Redis Bitmap 查询 (BITCOUNT)
    - Redis 失败时回退到数据库
    - 记录缓存命中/未命中
    - _Requirements: 2.1, 2.4, 8.2_

  - [ ]* 4.6 编写 countCompletedParts() 单元测试
    - 测试 Bitmap 查询成功场景
    - 测试 Redis 失败回退场景
    - _Requirements: 2.1, 8.2_

  - [x] 4.7 实现 findCompletedPartNumbers() 方法
    - 使用 BITCOUNT 获取总数
    - 遍历 Bitmap 获取所有已完成分片编号
    - 优化遍历性能（提前终止）
    - Redis 失败时回退到数据库
    - _Requirements: 2.2, 8.2_

  - [ ]* 4.8 编写 findCompletedPartNumbers() 属性测试
    - **Property 3: Bitmap 查询一致性**
    - **Validates: Requirements 2.1, 2.2**

  - [x] 4.9 实现 asyncSyncToDatabase() 异步同步方法
    - 比较 Bitmap 和数据库中的分片记录
    - 找出差异（新分片）
    - 批量插入到数据库
    - 异常处理（记录日志但不抛出）
    - _Requirements: 3.2, 3.3, 3.4_

  - [ ]* 4.10 编写 asyncSyncToDatabase() 属性测试
    - **Property 5: 定期同步触发条件**
    - **Validates: Requirements 3.1, 3.5**

  - [ ]* 4.11 编写 asyncSyncToDatabase() 属性测试
    - **Property 6: 同步差异处理**
    - **Validates: Requirements 3.2, 3.3**

  - [ ]* 4.12 编写 asyncSyncToDatabase() 属性测试
    - **Property 7: 同步失败不影响上传**
    - **Validates: Requirements 3.4**

  - [x] 4.13 实现 syncAllPartsToDatabase() 全量同步方法
    - 从 Bitmap 获取所有分片
    - 批量插入到数据库
    - 删除 Bitmap 释放内存
    - 失败时抛出异常并保留 Bitmap
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 4.14 编写 syncAllPartsToDatabase() 属性测试
    - **Property 8: 全量同步数据完整性**
    - **Validates: Requirements 4.1, 4.5**

  - [ ]* 4.15 编写 syncAllPartsToDatabase() 属性测试
    - **Property 10: 同步成功后清理 Bitmap**
    - **Validates: Requirements 4.3**

  - [ ]* 4.16 编写 syncAllPartsToDatabase() 属性测试
    - **Property 11: 同步失败保留 Bitmap**
    - **Validates: Requirements 4.4**

  - [x] 4.17 实现 loadPartsFromDatabase() 断点续传方法
    - 从数据库查询已完成分片
    - 重建 Bitmap
    - 设置 TTL
    - _Requirements: 8.3, 8.4_

  - [ ]* 4.18 编写 loadPartsFromDatabase() 属性测试
    - **Property 20: 断点续传状态恢复**
    - **Validates: Requirements 8.3**

- [x] 5. Checkpoint - 核心功能验证
  - 确保所有 Repository 方法测试通过
  - 验证 Redis 操作正确性
  - 验证故障回退机制
  - 询问用户是否有问题

- [ ] 6. 实现 UploadPartMapper 数据访问层
  - [x] 6.1 创建 UploadPartMapper 接口
    - insert() 方法
    - insertOrIgnore() 方法（幂等性）
    - batchInsert() 方法
    - countByTaskId() 方法
    - findPartNumbersByTaskId() 方法
    - findByTaskId() 方法
    - deleteByTaskId() 方法
    - _Requirements: 4.2, 8.2_

  - [x] 6.2 创建 UploadPartMapper.xml MyBatis 映射文件
    - 实现 batchInsert 批量插入
    - 使用 ON CONFLICT DO NOTHING 保证幂等性
    - _Requirements: 4.2_

  - [x] 6.3 编写 UploadPartMapper 单元测试

    - 测试批量插入
    - 测试幂等性（重复插入）
    - 测试查询方法
    - _Requirements: 4.2_

- [x] 7. 实现监控指标
  - [x] 7.1 创建 BitmapMetrics 监控类
    - recordWriteSuccess() 方法
    - recordWriteFailure() 方法
    - recordFallback() 方法
    - recordCacheHit() 方法
    - recordCacheMiss() 方法
    - recordTiming() 方法
    - recordSync() 方法
    - registerCacheHitRatio() Gauge
    - registerActiveTasksGauge() Gauge
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 7.2 集成监控到 UploadPartRepositoryImpl
    - 在所有关键操作中记录指标
    - 记录操作耗时
    - 记录成功/失败计数
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 7.3 编写监控指标单元测试

    - 验证指标正确记录
    - 验证缓存命中率计算
    - _Requirements: 7.5_

- [x] 8. 参数校验和错误处理
  - [x] 8.1 在 savePart() 中添加参数校验
    - 校验分片编号范围（1 到 totalParts）
    - 校验 taskId 有效性
    - 抛出清晰的异常信息
    - _Requirements: 边界条件 7_

  - [x] 8.2 编写参数校验单元测试

    - 测试无效分片编号
    - 测试无效 taskId
    - _Requirements: 边界条件 7_

  - [x] 8.3 实现超大文件降级逻辑
    - 检查总分片数是否超过 maxParts
    - 超过限制时直接使用数据库模式
    - 记录日志
    - _Requirements: 边界条件 5_

  - [x] 8.4 编写超大文件降级测试


    - 测试超过 10000 分片的场景
    - 验证自动降级到数据库
    - _Requirements: 边界条件 5_

- [ ] 9. Checkpoint - 完整功能测试
  - 确保所有单元测试通过
  - 确保所有属性测试通过
  - 验证监控指标正确记录
  - 询问用户是否有问题

- [ ] 10. 集成测试
  - [x] 10.1 创建 BitmapIntegrationTest 集成测试类
    - 使用 Testcontainers 启动 PostgreSQL 和 Redis
    - 测试完整上传流程（初始化 → 上传分片 → 完成）
    - 测试 Redis 故障场景
    - 测试断点续传场景
    - 测试并发上传场景
    - _Requirements: 8.1, 8.5, 5.1, 5.2, 8.3_

  - [ ]* 10.2 编写完整上传流程属性测试
    - **Property 21: 完成任务数据一致性**
    - **Validates: Requirements 8.1, 8.5**

  - [ ]* 10.3 编写性能对比测试
    - **Property 22: 数据库写入减少**
    - **Validates: Requirements 9.3**

- [x] 11. 配置文件和文档
  - [x] 11.1 更新 application.yml 配置
    - 添加 storage.multipart.bitmap 配置段
    - 配置 enabled、sync-batch-size、expire-hours、max-parts
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 11.2 创建 application-test.yml 测试配置
    - 配置测试环境的 Bitmap 参数
    - 配置 Redis 和 PostgreSQL 连接
    - _Requirements: 测试策略_

  - [ ]* 11.3 更新 README.md 文档
    - 添加 Bitmap 优化功能说明
    - 添加配置说明
    - 添加性能对比数据
    - _Requirements: 文档_

- [x] 12. 监控和可观测性配置
  - [x] 12.1 创建 Prometheus 配置文件

    - docker/monitoring/prometheus.yml
    - 配置 scrape 目标
    - _Requirements: 7.5_

  - [x] 12.2 创建 Grafana 数据源配置
    - docker/monitoring/grafana/datasources/prometheus.yml
    - _Requirements: 7.5_

  - [x] 12.3 创建 Grafana 仪表板

    - docker/monitoring/grafana/dashboards/bitmap-monitoring.json
    - 包含写入成功率、缓存命中率、回退次数等面板
    - _Requirements: 7.5_

  - [x] 12.4 创建 docker-compose.monitoring.yml

    - 配置 Prometheus 和 Grafana 服务
    - _Requirements: 7.5_

  - [x] 12.5 创建监控启动脚本

    - scripts/start-monitoring.ps1
    - _Requirements: 7.5_

- [ ] 13. 最终验证和性能测试
  - [x] 13.1 运行完整测试套件

    - 运行所有单元测试
    - 运行所有属性测试
    - 运行所有集成测试
    - 确保测试覆盖率 > 80%
    - _Requirements: 测试策略_

  - [ ] 13.2 执行性能基准测试
    - 测试 1000 个分片上传性能
    - 对比 Bitmap 模式和数据库模式
    - 验证至少 5 倍性能提升
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 13.3 执行压力测试

    - 测试 100 个并发上传任务
    - 验证响应时间 < 100ms
    - 验证系统稳定性
    - _Requirements: 9.5_

  - [ ]* 13.4 验证监控指标
    - 启动 Prometheus 和 Grafana
    - 执行上传操作
    - 验证指标正确采集和展示
    - _Requirements: 7.5_

- [ ] 14. Final Checkpoint - 完成验证
  - 确保所有测试通过
  - 确保性能目标达成
  - 确保监控正常工作
  - 询问用户是否满意

## Notes

- 任务标记 `*` 的为可选任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求编号，便于追溯
- Checkpoint 任务确保渐进式验证
- 属性测试每个运行 100 次随机输入
- 单元测试和属性测试互补，共同保证系统正确性
- 集成测试使用 Testcontainers 确保真实环境测试
- 监控配置为可选任务，但强烈建议实施以便观察优化效果
