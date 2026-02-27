# 需求文档

## 简介

本文档描述文件服务模块化优化功能的需求。该功能旨在解决当前文件服务的三个核心痛点：私有存储桶导致文件无法访问、缺少文件管理功能、缺少租户管理功能。通过实施双 Bucket 策略、租户管理和文件管理功能，提升系统的可用性、可管理性和多租户支持能力。

## 术语表

- **File_Service**: 文件服务系统，负责文件的上传、下载、存储和管理
- **Storage_Service**: 存储服务接口，负责与底层存储系统（如 S3）交互
- **Public_Bucket**: 公开存储桶，存储可公开访问的文件
- **Private_Bucket**: 私有存储桶，存储需要权限控制的文件
- **Tenant**: 租户，使用文件服务的应用或组织单位
- **Tenant_Manager**: 租户管理器，负责租户的创建、配置和配额管理
- **Quota_Checker**: 配额检查器，负责验证租户的存储配额和文件数量限制
- **File_Manager**: 文件管理器，负责文件的查询、删除和统计
- **Access_Level**: 访问级别，定义文件的访问权限（PUBLIC 或 PRIVATE）
- **Presigned_URL**: 预签名 URL，用于临时授权访问私有文件的 URL
- **Admin_API**: 管理员 API，提供文件和租户的管理功能
- **Audit_Logger**: 审计日志记录器，记录管理员操作日志

## 需求

### 需求 1: 双 Bucket 存储策略

**用户故事**: 作为系统管理员，我希望系统支持公开和私有两种存储桶，以便公开文件可以直接访问，私有文件通过权限控制访问。

#### 验收标准

1. THE Storage_Service SHALL 支持配置两个独立的存储桶：Public_Bucket 和 Private_Bucket
2. WHEN 上传文件时，THE File_Service SHALL 根据 Access_Level 选择对应的存储桶
3. WHEN Access_Level 为 PUBLIC 时，THE File_Service SHALL 将文件存储到 Public_Bucket
4. WHEN Access_Level 为 PRIVATE 时，THE File_Service SHALL 将文件存储到 Private_Bucket
5. THE Public_Bucket SHALL 配置允许匿名读取的桶策略
6. THE Private_Bucket SHALL 保持默认私有访问策略

### 需求 2: 文件访问 URL 生成

**用户故事**: 作为应用开发者，我希望系统能根据文件的访问级别生成正确的访问 URL，以便用户可以访问文件。

#### 验收标准

1. WHEN 请求公开文件的 URL 时，THE File_Service SHALL 返回 Public_Bucket 的直接访问 URL
2. WHEN 请求私有文件的 URL 时，THE File_Service SHALL 验证请求用户的访问权限
3. IF 用户无权访问私有文件，THEN THE File_Service SHALL 拒绝请求并返回错误
4. WHEN 用户有权访问私有文件时，THE File_Service SHALL 生成带有过期时间的 Presigned_URL
5. THE Presigned_URL SHALL 在配置的时间后自动过期

### 需求 3: 租户数据模型

**用户故事**: 作为系统架构师，我希望系统能够存储和管理租户信息，以便支持多租户场景。

#### 验收标准

1. THE File_Service SHALL 维护租户信息表，包含租户 ID、名称、状态、配额配置和联系信息
2. THE File_Service SHALL 维护租户使用统计表，记录已使用的存储空间和文件数量
3. THE File_Service SHALL 支持三种租户状态：active（活跃）、suspended（停用）、deleted（已删除）
4. WHEN 创建新租户时，THE File_Service SHALL 初始化默认配额配置
5. THE File_Service SHALL 记录租户的最后上传时间

### 需求 4: 租户配额管理

**用户故事**: 作为系统管理员，我希望能够为每个租户设置存储配额和文件数量限制，以便控制资源使用。

#### 验收标准

1. THE Tenant_Manager SHALL 支持配置租户的最大存储空间（max_storage_bytes）
2. THE Tenant_Manager SHALL 支持配置租户的最大文件数量（max_file_count）
3. THE Tenant_Manager SHALL 支持配置租户的单文件大小限制（max_single_file_size）
4. THE Tenant_Manager SHALL 支持配置租户允许的文件类型列表（allowed_file_types）
5. WHEN 更新租户配额时，THE Tenant_Manager SHALL 验证新配额的有效性
6. THE Tenant_Manager SHALL 记录配额更新的时间戳

### 需求 5: 上传前配额检查

**用户故事**: 作为文件服务，我希望在文件上传前检查租户配额，以便防止超出限制的上传。

#### 验收标准

1. WHEN 用户发起文件上传时，THE Quota_Checker SHALL 验证租户状态为 active
2. IF 租户状态不是 active，THEN THE Quota_Checker SHALL 拒绝上传并返回租户停用错误
3. WHEN 租户状态为 active 时，THE Quota_Checker SHALL 检查当前使用量加上新文件大小是否超过最大存储空间
4. IF 存储空间超限，THEN THE Quota_Checker SHALL 拒绝上传并返回配额超限错误
5. WHEN 存储空间未超限时，THE Quota_Checker SHALL 检查当前文件数量是否已达到最大文件数量
6. IF 文件数量超限，THEN THE Quota_Checker SHALL 拒绝上传并返回文件数量超限错误
7. WHEN 文件数量未超限时，THE Quota_Checker SHALL 检查文件大小是否超过单文件大小限制
8. IF 单文件大小超限，THEN THE Quota_Checker SHALL 拒绝上传并返回文件过大错误

### 需求 6: 租户管理 API

**用户故事**: 作为系统管理员，我希望通过 API 管理租户，以便创建、查询、更新和删除租户。

#### 验收标准

1. THE Admin_API SHALL 提供创建租户的接口，接受租户名称、配额配置和联系信息
2. THE Admin_API SHALL 提供查询租户列表的接口，支持分页和状态过滤
3. THE Admin_API SHALL 提供查询单个租户详情的接口，包含配额使用情况
4. THE Admin_API SHALL 提供更新租户配置的接口，允许修改配额和联系信息
5. THE Admin_API SHALL 提供更新租户状态的接口，支持启用和停用操作
6. THE Admin_API SHALL 提供删除租户的接口，将租户状态标记为 deleted
7. WHEN 删除租户时，THE Admin_API SHALL 保留租户的历史数据用于审计

### 需求 7: 文件查询和过滤

**用户故事**: 作为系统管理员，我希望能够查询和过滤文件列表，以便管理和监控文件。

#### 验收标准

1. THE File_Manager SHALL 提供文件列表查询接口，支持分页
2. THE File_Manager SHALL 支持按租户 ID 过滤文件
3. THE File_Manager SHALL 支持按用户 ID 过滤文件
4. THE File_Manager SHALL 支持按文件类型（content type）过滤文件
5. THE File_Manager SHALL 支持按访问级别（Access_Level）过滤文件
6. THE File_Manager SHALL 支持按上传时间范围过滤文件
7. THE File_Manager SHALL 支持按文件大小范围过滤文件
8. THE File_Manager SHALL 支持按指定字段排序，默认按创建时间降序排列
9. THE File_Manager SHALL 提供查询单个文件详情的接口

### 需求 8: 文件删除功能

**用户故事**: 作为系统管理员，我希望能够删除文件，以便清理不需要的文件和释放存储空间。

#### 验收标准

1. THE File_Manager SHALL 提供删除单个文件的接口
2. WHEN 删除文件时，THE File_Manager SHALL 从存储系统中删除文件对象
3. WHEN 删除文件时，THE File_Manager SHALL 从数据库中删除文件记录
4. WHEN 删除文件时，THE File_Manager SHALL 更新租户的使用统计，减少存储空间和文件数量
5. THE File_Manager SHALL 提供批量删除文件的接口，接受文件 ID 列表
6. WHEN 批量删除时，THE File_Manager SHALL 对每个文件执行删除操作
7. IF 某个文件删除失败，THEN THE File_Manager SHALL 继续删除其他文件并记录失败信息

### 需求 9: 存储统计功能

**用户故事**: 作为系统管理员，我希望查看存储统计信息，以便了解系统的使用情况。

#### 验收标准

1. THE File_Manager SHALL 提供全局存储统计接口，返回总文件数和总存储空间
2. THE File_Manager SHALL 统计公开文件和私有文件的数量
3. THE File_Manager SHALL 统计各文件类型的数量分布
4. THE File_Manager SHALL 提供按租户统计的接口，返回每个租户的存储使用情况
5. THE File_Manager SHALL 在统计响应中包含统计时间戳

### 需求 10: 审计日志记录

**用户故事**: 作为系统管理员，我希望系统记录所有管理操作的审计日志，以便追踪和审计。

#### 验收标准

1. THE Audit_Logger SHALL 记录所有管理员操作，包含操作者 ID、操作类型、目标类型和目标 ID
2. THE Audit_Logger SHALL 记录操作的租户 ID（如果适用）
3. THE Audit_Logger SHALL 记录操作的详细信息（JSON 格式）
4. THE Audit_Logger SHALL 记录操作者的 IP 地址
5. THE Audit_Logger SHALL 记录操作的时间戳
6. THE Audit_Logger SHALL 支持记录以下操作类型：DELETE_FILE、BATCH_DELETE_FILES、CREATE_TENANT、UPDATE_TENANT、SUSPEND_TENANT、UPDATE_QUOTA
7. THE Audit_Logger SHALL 为审计日志表创建索引，支持按操作者、操作类型和时间查询

### 需求 11: 存储路径生成

**用户故事**: 作为文件服务，我希望生成结构化的存储路径，以便组织和管理文件。

#### 验收标准

1. THE File_Service SHALL 按照格式 `{bucket}/{appId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}` 生成存储路径
2. WHEN 生成存储路径时，THE File_Service SHALL 使用文件上传时的日期作为路径的日期部分
3. THE File_Service SHALL 根据文件的 MIME 类型确定路径中的 type 部分（如 images、files、videos）
4. THE File_Service SHALL 使用文件的唯一 ID 和原始扩展名作为文件名

### 需求 12: 配额使用统计更新

**用户故事**: 作为文件服务，我希望在文件上传和删除时自动更新租户的使用统计，以便保持统计数据的准确性。

#### 验收标准

1. WHEN 文件上传成功时，THE File_Service SHALL 增加租户的已使用存储空间
2. WHEN 文件上传成功时，THE File_Service SHALL 增加租户的已使用文件数量
3. WHEN 文件上传成功时，THE File_Service SHALL 更新租户的最后上传时间
4. WHEN 文件删除成功时，THE File_Service SHALL 减少租户的已使用存储空间
5. WHEN 文件删除成功时，THE File_Service SHALL 减少租户的已使用文件数量
6. THE File_Service SHALL 确保使用统计的更新操作是原子性的

### 需求 13: 管理员 API 认证

**用户故事**: 作为系统架构师，我希望管理员 API 有独立的认证机制，以便保护管理功能的安全。

#### 验收标准

1. THE Admin_API SHALL 要求所有请求包含有效的认证凭证
2. THE Admin_API SHALL 支持 API Key 认证方式
3. WHEN 使用 API Key 认证时，THE Admin_API SHALL 验证请求头中的 X-Admin-Api-Key
4. IF API Key 无效或缺失，THEN THE Admin_API SHALL 拒绝请求并返回 401 未授权错误
5. THE Admin_API SHALL 支持配置多个 API Key，每个 Key 有独立的名称和权限

### 需求 14: 租户自动创建

**用户故事**: 作为应用开发者，我希望系统能够自动创建不存在的租户，以便简化集成流程。

#### 验收标准

1. WHERE 启用租户自动创建功能时，WHEN 遇到未知的租户 ID 时，THE Tenant_Manager SHALL 自动创建该租户
2. WHEN 自动创建租户时，THE Tenant_Manager SHALL 使用系统配置的默认配额
3. WHEN 自动创建租户时，THE Tenant_Manager SHALL 将租户状态设置为 active
4. WHERE 禁用租户自动创建功能时，WHEN 遇到未知的租户 ID 时，THE Tenant_Manager SHALL 返回租户不存在错误

### 需求 15: 错误处理和异常

**用户故事**: 作为应用开发者，我希望系统提供清晰的错误信息，以便快速定位和解决问题。

#### 验收标准

1. WHEN 租户不存在时，THE File_Service SHALL 返回 TenantNotFoundException 异常
2. WHEN 租户被停用时，THE File_Service SHALL 返回 TenantSuspendedException 异常
3. WHEN 配额超限时，THE File_Service SHALL 返回 QuotaExceededException 异常，包含具体的超限类型
4. WHEN 文件过大时，THE File_Service SHALL 返回 FileTooLargeException 异常，包含文件大小和限制大小
5. WHEN 用户无权访问文件时，THE File_Service SHALL 返回 AccessDeniedException 异常
6. THE File_Service SHALL 为所有异常提供清晰的错误消息和错误代码
