# 实施计划：文件服务模块化优化

## 概述

本实施计划将文件服务优化分为四个主要阶段：双 Bucket 策略、租户管理、文件管理和审计日志。每个阶段包含具体的实施任务，按照依赖关系组织，确保增量开发和及时验证。

## 任务

- [x] 1. 阶段 1：双 Bucket 存储策略
  - [x] 1.1 扩展 S3Properties 配置类支持双桶
    - 添加 publicBucket 和 privateBucket 配置属性
    - 添加 cdnDomain 可选配置属性
    - 更新 application.yml 配置示例
    - _需求：1.1_
  
  - [x] 1.2 扩展 S3StorageService 接口和实现
    - 添加 uploadToPublicBucket 方法
    - 添加 uploadToPrivateBucket 方法
    - 添加 generatePresignedUrl 方法
    - 添加 getPublicUrl 方法
    - 修改现有 uploadFile 方法根据 AccessLevel 选择桶
    - _需求：1.2, 1.3, 1.4_
  
  - [x] 1.3 编写属性测试：文件存储桶选择

    - **属性 1：文件存储桶选择**
    - **验证需求：1.2**
  
  - [x] 1.4 实现 BucketPolicyUtil 工具类
    - 实现 generatePublicBucketPolicy 方法
    - 实现 applyBucketPolicy 方法
    - _需求：1.5_
  
  - [x] 1.5 实现 FileAccessService
    - 实现 getFileUrl 方法，根据访问级别返回正确的 URL
    - 实现 canAccessFile 方法（传入 FileRecord 对象避免重复查询），验证用户访问权限
    - 实现 updateAccessLevel 方法
    - _需求：2.1, 2.2, 2.3, 2.4_
  
  - [x] 1.6 编写属性测试：URL 生成和权限验证

    - **属性 2：公开文件 URL 生成**
    - **验证需求：2.1**
    - **属性 3：私有文件权限验证**
    - **验证需求：2.2, 2.3**
    - **属性 4：私有文件预签名 URL 生成**
    - **验证需求：2.4**
  
  - [x] 1.7 更新 StoragePathGenerator 支持租户 ID
    - 修改路径生成格式为 `{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}`
    - _需求：11.1_
  
  - [x] 1.8 编写属性测试：存储路径格式

    - **属性 29：存储路径格式正确性**
    - **验证需求：11.1**
  
  - [x] 1.9 检查点：验证双 Bucket 功能
    - 确保所有测试通过，如有问题请询问用户

- [x] 2. 阶段 2：租户管理
  - [x] 2.1 创建数据库迁移脚本
    - 创建 tenants 表的 Flyway 迁移脚本
    - 创建 tenant_usage 表的 Flyway 迁移脚本（注意：不使用物理外键）
    - 适配 file_records 表的 app_id 字段作为 tenant_id（如已存在 app_id，可复用或创建别名/视图）
    - 创建必要的索引
    - _需求：3.1, 3.2_
  
  - [x] 2.2 实现 Tenant 领域模型
    - 创建 Tenant 类，包含所有字段和 TenantStatus 枚举
    - 实现业务方法：suspend、activate、markDeleted
    - _需求：3.3_
  
  - [x] 2.3 实现 TenantUsage 领域模型
    - 创建 TenantUsage 类
    - 实现业务方法：incrementUsage、decrementUsage
    - _需求：3.2_
  
  - [x] 2.4 实现 TenantRepository 和 TenantUsageRepository
    - 创建 TenantMapper 接口（MyBatis）
    - 创建 TenantRepositoryImpl 实现类
    - 创建 TenantUsageMapper 接口（MyBatis）
    - 创建 TenantUsageRepositoryImpl 实现类
    - 实现 incrementUsage 原子更新：`UPDATE ... SET used_storage_bytes = used_storage_bytes + ?`
    - 实现 decrementUsage 原子更新：`UPDATE ... SET used_storage_bytes = GREATEST(0, used_storage_bytes - ?)`
    - 编写对应的 XML 映射文件
    - _需求：3.1, 3.2, 12.6_

  - [x] 2.5 实现 TenantProperties 配置类
    - 创建 TenantProperties 配置类
    - 添加默认配额配置（defaultMaxStorageBytes、defaultMaxFileCount、defaultMaxSingleFileSize）
    - 添加 autoCreate 配置
    - _需求：14.1, 14.4_
  
  - [x] 2.6 实现 TenantDomainService
    - 实现 checkQuota 方法，验证租户配额
    - 实现 getOrCreateTenant 方法，支持自动创建租户
    - 实现 createDefaultTenant 私有方法
    - _需求：3.4, 5.1, 5.3, 5.5, 5.7, 14.1, 14.4_
  
  - [x] 2.7 编写属性测试：租户配额检查

    - **属性 9：非活跃租户上传拒绝**
    - **验证需求：5.1**
    - **属性 10：存储空间配额检查**
    - **验证需求：5.3**
    - **属性 11：文件数量配额检查**
    - **验证需求：5.5**
    - **属性 12：单文件大小限制检查**
    - **验证需求：5.7**
  
  - [x] 2.8 编写属性测试：租户创建和配额初始化

    - **属性 5：新租户默认配额初始化**
    - **验证需求：3.4**
    - **属性 34：租户自动创建**
    - **验证需求：14.1**
    - **属性 35：租户自动创建禁用**
    - **验证需求：14.4**
  
  - [x] 2.9 实现 TenantManagementService
    - 实现 createTenant 方法（同时创建 TenantUsage 记录）
    - 实现 updateTenant 方法
    - 实现 updateTenantStatus 方法
    - 实现 deleteTenant 方法（软删除）
    - 实现 getTenantDetail 方法
    - 实现 listTenants 方法
    - _需求：6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  
  - [x] 2.10 编写属性测试：租户管理

    - **属性 7：租户配额有效性验证**
    - **验证需求：4.5**
    - **属性 8：配额更新时间戳记录**
    - **验证需求：4.6**
    - **属性 13：租户软删除保留数据**
    - **验证需求：6.7**
  
  - [x] 2.11 实现 TenantAdminController
    - 实现 POST /api/v1/admin/tenants 创建租户接口
    - 实现 GET /api/v1/admin/tenants 查询租户列表接口
    - 实现 GET /api/v1/admin/tenants/{tenantId} 查询租户详情接口
    - 实现 PUT /api/v1/admin/tenants/{tenantId} 更新租户接口
    - 实现 PUT /api/v1/admin/tenants/{tenantId}/status 更新租户状态接口
    - 实现 DELETE /api/v1/admin/tenants/{tenantId} 删除租户接口
    - _需求：6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  
  - [x] 2.12 编写单元测试：租户管理 API

    - 测试创建租户接口
    - 测试查询租户列表接口（分页和过滤）
    - 测试查询租户详情接口
    - 测试更新租户接口
    - 测试更新租户状态接口
    - 测试删除租户接口
  
  - [x] 2.13 集成配额检查到上传流程
    - 在 UploadApplicationService 中集成 TenantDomainService.checkQuota
    - 在上传成功后调用 TenantUsageRepository.incrementUsage（原子操作）
    - _需求：5.1, 5.3, 5.5, 5.7, 12.1, 12.6_
  
  - [x] 2.14 编写属性测试：上传统计更新

    - **属性 6：租户最后上传时间更新**
    - **验证需求：3.5**
    - **属性 30：上传成功统计增加**
    - **验证需求：12.1**
  
  - [x] 2.15 检查点：验证租户管理功能
    - 确保所有测试通过，如有问题请询问用户

- [x] 3. 阶段 3：文件管理
  - [x] 3.1 实现 FileQuery 查询对象
    - 创建 FileQuery 类，包含所有过滤和分页参数
    - _需求：7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_
  
  - [x] 3.2 扩展 FileRecordRepository 支持高级查询
    - 添加 findByQuery 方法，支持复杂过滤和排序
    - 添加 countByQuery 方法
    - 更新 FileRecordMapper 和 XML 映射文件
    - _需求：7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_
  
  - [x] 3.3 编写属性测试：文件查询过滤

    - **属性 14：文件查询租户过滤**
    - **验证需求：7.2**
    - **属性 15：文件查询用户过滤**
    - **验证需求：7.3**
    - **属性 16：文件查询内容类型过滤**
    - **验证需求：7.4**
    - **属性 17：文件查询访问级别过滤**
    - **验证需求：7.5**
    - **属性 18：文件查询时间范围过滤**
    - **验证需求：7.6**
    - **属性 19：文件查询大小范围过滤**
    - **验证需求：7.7**
    - **属性 20：文件查询结果排序**
    - **验证需求：7.8**
  
  - [x] 3.4 实现 StorageStatistics 和相关响应对象
    - 创建 StorageStatistics 类
    - 创建 TenantStorageStats 类
    - 创建 BatchDeleteResult 类
    - _需求：9.1, 9.2, 9.3, 9.4, 9.5_
  
  - [x] 3.5 实现 FileManagementService
    - 实现 listFiles 方法
    - 实现 getFileDetail 方法
    - 实现 deleteFile 方法（删除存储对象、数据库记录、调用 decrementUsage 更新统计）
    - 实现 batchDeleteFiles 方法（对每个文件调用 deleteFile，失败继续处理）
    - 实现 getStorageStatistics 方法
    - 实现 getStorageStatisticsByTenant 方法
    - _需求：7.1, 7.9, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 9.1, 9.2, 9.3, 9.4, 9.5, 12.4, 12.5_
  
  - [x] 3.6 编写属性测试：文件删除

    - **属性 21：文件删除存储清理**
    - **验证需求：8.2**
    - **属性 22：文件删除记录清理**
    - **验证需求：8.3**
    - **属性 23：文件删除统计更新**
    - **验证需求：8.4**
    - **属性 24：批量删除完整性**
    - **验证需求：8.6, 8.7**
    - **属性 31：删除成功统计减少**
    - **验证需求：12.4**
  
  - [x] 3.7 编写属性测试：存储统计
    - **属性 25：存储统计访问级别分类**
    - **验证需求：9.2**
    - **属性 26：存储统计文件类型分布**
    - **验证需求：9.3**
    - **属性 27：统计响应时间戳**
    - **验证需求：9.5**
  
  - [x] 3.8 实现 FileAdminController
    - 实现 GET /api/v1/admin/files 查询文件列表接口
    - 实现 GET /api/v1/admin/files/{fileId} 查询文件详情接口
    - 实现 DELETE /api/v1/admin/files/{fileId} 删除文件接口
    - 实现 POST /api/v1/admin/files/batch-delete 批量删除接口
    - 实现 GET /api/v1/admin/files/statistics 存储统计接口
    - 实现 GET /api/v1/admin/files/statistics/by-tenant 按租户统计接口
    - _需求：7.1, 7.9, 8.1, 8.5, 9.1, 9.4_
  
  - [x] 3.9 编写单元测试：文件管理 API

    - 测试查询文件列表接口（各种过滤条件）
    - 测试查询文件详情接口
    - 测试删除文件接口
    - 测试批量删除接口
    - 测试存储统计接口
    - 测试按租户统计接口
  
  - [x] 3.10 检查点：验证文件管理功能
    - 确保所有测试通过，如有问题请询问用户

- [x] 4. 阶段 4：审计日志和认证
  - [x] 4.1 创建审计日志数据库迁移脚本
    - 创建 admin_audit_logs 表的 Flyway 迁移脚本
    - 创建必要的索引
    - _需求：10.1, 10.7_
  
  - [x] 4.2 实现 AuditLog 领域模型
    - 创建 AuditLog 类
    - 创建 AuditAction 和 TargetType 枚举
    - _需求：10.1, 10.6_
  
  - [x] 4.3 实现 AuditLogRepository
    - 创建 AuditLogMapper 接口（MyBatis）
    - 创建 AuditLogRepositoryImpl 实现类
    - 编写对应的 XML 映射文件
    - _需求：10.1_
  
  - [x] 4.4 实现 AuditLogService
    - 实现 log 方法，记录审计日志
    - 实现 queryLogs 方法，查询审计日志
    - _需求：10.1_
  
  - [x] 4.5 编写属性测试：审计日志记录

    - **属性 28：审计日志完整记录**
    - **验证需求：10.1**
    - **测试状态：✓ 通过**
    - **测试结果：**
      - Property 28 (审计日志完整记录): 100 tries, 0 failures
      - 审计日志记录失败不应中断业务操作: 50 tries, 0 failures
  
  - [x] 4.6 实现 AdminProperties 配置类
    - 创建 AdminProperties 配置类
    - 创建 ApiKeyConfig 内部类
    - 添加 apiKeys 配置列表
    - _需求：13.2, 13.5_
  
  - [x] 4.7 实现 AdminContext 上下文类
    - 创建 AdminContext 类，使用 ThreadLocal 存储管理员信息
    - 实现 setAdminUser、getAdminUser、setIpAddress、getIpAddress、clear 方法
    - _需求：13.1_
  
  - [x] 4.8 实现 ApiKeyAuthFilter
    - 创建 ApiKeyAuthFilter 类，继承 OncePerRequestFilter
    - 实现 doFilterInternal 方法，验证 X-Admin-Api-Key 请求头
    - 实现 isValidApiKey 私有方法
    - 设置 AdminContext
    - _需求：13.1, 13.2, 13.3, 13.4_
  
  - [x] 4.9 编写属性测试：管理员 API 认证

    - **属性 33：管理员 API 认证要求**
    - **验证需求：13.1**
  
  - [x] 4.10 编写单元测试：API Key 认证

    - 测试有效 API Key 通过认证
    - 测试无效 API Key 被拒绝
    - 测试缺失 API Key 被拒绝
    - 测试非管理员 API 不需要认证
  
  - [x] 4.11 集成审计日志到管理员操作
    - 在 FileManagementService.deleteFile 中记录审计日志
    - 在 FileManagementService.batchDeleteFiles 中记录审计日志
    - 在 TenantManagementService.createTenant 中记录审计日志
    - 在 TenantManagementService.updateTenant 中记录审计日志
    - 在 TenantManagementService.updateTenantStatus 中记录审计日志
    - 在 TenantManagementService.deleteTenant 中记录审计日志
    - _需求：10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  
  - [x] 4.12 配置 WebConfig 注册过滤器
    - 在 WebConfig 中注册 ApiKeyAuthFilter
    - 配置过滤器顺序
    - _需求：13.1_
  
  - [x] 4.13 检查点：验证审计日志和认证功能
    - 确保所有测试通过，如有问题请询问用户

- [x] 5. 阶段 5：并发安全和异常处理
  - [x] 5.1 实现自定义异常类
    - 创建 TenantNotFoundException 异常类
    - 创建 TenantSuspendedException 异常类
    - 创建 QuotaExceededException 异常类
    - 创建 FileTooLargeException 异常类
    - 创建 AccessDeniedException 异常类
    - 为每个异常添加错误代码和详细信息
    - _需求：15.1, 15.2, 15.3, 15.4, 15.5, 15.6_
  
  - [x] 5.2 编写单元测试：异常消息

    - **属性 36：异常消息清晰性**
    - **验证需求：15.6**
  
  - [x] 5.3 扩展 GlobalExceptionHandler
    - 添加 TenantNotFoundException 处理器
    - 添加 TenantSuspendedException 处理器
    - 添加 QuotaExceededException 处理器
    - 添加 FileTooLargeException 处理器
    - 添加 AccessDeniedException 处理器
    - 确保错误响应格式统一
    - _需求：15.1, 15.2, 15.3, 15.4, 15.5, 15.6_
  
  - [x] 5.4 实现租户使用统计的原子性更新
    - 在 TenantUsageRepository 中使用数据库事务
    - 考虑使用乐观锁或悲观锁处理并发更新
    - _需求：12.6_
  
  - [x] 5.5 编写属性测试：统计更新原子性

    - **属性 32：统计更新原子性**
    - **验证需求：12.6**
  
  - [x] 5.6 检查点：验证并发安全和异常处理
    - 确保所有测试通过，如有问题请询问用户

- [x] 6. 阶段 6：集成测试和文档
  - [ ]* 6.1 编写端到端集成测试
    - 测试完整的文件上传流程（配额检查 → 上传 → 统计更新）
    - 测试完整的文件删除流程（删除 → 统计更新 → 审计日志）
    - 测试租户管理流程（创建 → 更新 → 停用 → 删除）
    - 测试公开文件和私有文件的访问 URL 生成
    - 测试管理员 API 认证和授权
  
  - [ ]* 6.2 编写性能测试
    - 测试并发上传的性能和稳定性
    - 测试大文件上传的性能
    - 测试批量删除的性能
    - 测试统计查询的性能
  
  - [x] 6.3 更新 API 文档
    - 更新 API.md 文档，添加租户管理 API
    - 添加文件管理 API 文档
    - 添加认证说明
    - _需求：所有_
  
  - [x] 6.4 更新配置文档
    - 更新 README.md，添加双 Bucket 配置说明
    - 添加租户管理配置说明
    - 添加管理员 API Key 配置说明
    - _需求：所有_
  
  - [x] 6.5 创建数据库初始化脚本
    - 创建公开桶和私有桶的初始化脚本
    - 创建默认租户的初始化脚本（可选）
    - 创建管理员 API Key 的配置示例
    - _需求：1.5, 1.6_
  
  - [x] 6.6 最终检查点：完整功能验证
    - 运行所有测试（单元测试、属性测试、集成测试）
    - 验证所有功能正常工作
    - 如有问题请询问用户

## 注意事项

- 标记为 `*` 的任务是可选的测试任务，可以根据需要跳过以加快 MVP 开发
- 每个检查点任务都应该确保所有测试通过，如有问题及时向用户反馈
- 属性测试应该使用 jqwik 库，每个测试运行至少 100 次迭代
- 所有管理员 API 都需要通过 X-Admin-Api-Key 请求头进行认证
- 租户使用统计的更新必须是原子性的，避免并发问题
- 审计日志应该记录所有管理员操作，包含完整的上下文信息
