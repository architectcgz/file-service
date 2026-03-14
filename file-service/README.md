# File Service

通用平台级文件服务，支持多应用隔离的文件上传、存储和访问管理。

## 概述

File Service 是从 `blog-upload` 重构而来的通用文件服务，提供：

- **多租户隔离**: 通过 `X-App-Id` 请求头实现不同应用的文件数据隔离
- **文件去重**: 同一应用内相同文件自动去重，节省存储空间
- **多种上传方式**: 支持直接上传、分片上传、预签名 URL 上传、秒传
- **独立部署**: 拥有独立数据库和存储，与其他服务完全解耦
- **S3 兼容存储**: 当前默认使用 MinIO，也兼容标准 S3 接口

## 上传方式说明

- `/api/v1/upload/image`、`/api/v1/upload/file`: 普通上传，请求先到 `file-service`
- `/api/v1/multipart/*`: 服务端中转的分片上传，前端把分片发给 `file-service`
- `/api/v1/direct-upload/*`: 分片预签名直传，前端拿到分片 URL 后直接上传到 MinIO/S3
- `/api/v1/upload/presign` + `/api/v1/upload/confirm`: 单文件预签名直传

如果是大文件且希望减轻服务端带宽压力，优先使用 `/api/v1/direct-upload/*`。如果更看重服务端统一控制和兼容性，可以使用 `/api/v1/multipart/*`。

`/api/v1/direct-upload/*` 当前已支持：

- 基于 `fileHash` 的断点续传匹配
- 通过 `/api/v1/direct-upload/{taskId}/progress` 恢复已完成分片和 `etag`
- `complete` 阶段以对象存储中的实际分片为准，减少前端丢失本地状态后的失败率

## 核心特性

### 1. 双 Bucket 存储策略

系统使用双 Bucket 策略来管理文件访问权限：

**公开存储桶 (platform-files-public)**:
- 存储访问级别为 PUBLIC 的文件
- 配置了公开读取策略，任何人都可以直接访问
- 适用于公开内容（如博客文章图片、公开文档等）
- 返回直接访问 URL，无需预签名

**私有存储桶 (platform-files-private)**:
- 存储访问级别为 PRIVATE 的文件
- 保持默认私有访问策略
- 适用于私密内容（如聊天文件、私人文档等）
- 返回带过期时间的预签名 URL

**访问级别**:
- `PUBLIC`: 文件存储在公开桶，任何人都可以访问
- `PRIVATE`: 文件存储在私有桶，只有所有者可以访问

### 2. 租户管理

系统支持多租户管理，每个租户（应用）有独立的配额和使用统计：

**租户配额**:
- 最大存储空间限制
- 最大文件数量限制
- 单文件大小限制
- 允许的文件类型限制

**租户状态**:
- `ACTIVE`: 活跃状态，可以正常上传文件
- `SUSPENDED`: 停用状态，禁止上传文件
- `DELETED`: 已删除状态，保留历史数据

**自动创建租户**:
- 可配置是否自动创建未知租户
- 自动创建的租户使用默认配额

### 3. 应用隔离 (App ID / Tenant ID)

所有 API 请求必须携带 `X-App-Id` 请求头，用于标识请求来源的应用（租户）：

```http
X-App-Id: blog
```

**支持的应用 ID**:
- `blog`: 博客系统
- `im`: 即时通讯系统
- 其他自定义应用 ID (小写字母、数字、下划线、连字符，最长 32 字符)

**注意**: `X-App-Id` 和 `tenantId` 在系统中是等价的概念。

### 4. 存储路径格式

文件存储路径包含存储桶、租户 ID 前缀，实现物理隔离：

```
/{bucket}/{tenantId}/{year}/{month}/{day}/{userId}/{type}/{uuid}.{ext}
```

**示例**:
- Blog 公开图片: `platform-files-public/blog/2026/01/21/12345/images/01JGXXX-XXX-XXX.webp`
- IM 私有文件: `platform-files-private/im/2026/01/21/67890/files/01JGXXX-XXX-XXX.pdf`

### 5. 文件去重

- 同一应用内，不同用户上传相同文件（MD5 相同）会共享物理存储
- 不同应用的相同文件独立存储，互不影响
- 采用引用计数机制，只有当所有引用删除后才删除物理文件

### 6. 管理员 API

系统提供管理员 API 用于租户管理和文件管理：

**租户管理**:
- 创建、查询、更新、删除租户
- 配置租户配额
- 启用/停用租户
- 查看租户使用统计

**文件管理**:
- 查询文件列表（支持多种过滤条件）
- 查看文件详情
- 删除文件（单个或批量）
- 查看存储统计

**认证方式**: 管理员 API 使用 API Key 认证，需要在请求头中携带 `X-Admin-Api-Key`。

### 7. 独立数据库

File Service 使用独立的 PostgreSQL 数据库 `file_service`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/file_service
```

## 快速开始

### 前置要求

- Java 17+
- PostgreSQL 14+
- MinIO
- Maven 3.8+

### 本地开发

1. **启动依赖服务**:

```bash
cd file-service/docker
docker-compose up -d
```

这将启动：
- PostgreSQL (端口 5432)
- MinIO API (端口 9000)
- MinIO Console (端口 9001)
- File Gateway (端口 8090)

2. **配置应用**:

编辑 `src/main/resources/application.yml`，确认数据库和 S3 配置正确。

3. **运行服务**:

```bash
mvn spring-boot:run
```

服务将在 `http://localhost:8089` 启动。

### Docker 部署

详见 [docker/README.md](docker/README.md)

## API 使用

### 上传图片

```bash
curl -X POST http://localhost:8089/api/v1/upload/image \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@photo.jpg"
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX...",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/xxx.webp",
    "originalName": "photo.jpg",
    "fileSize": 102400,
    "contentType": "image/webp"
  }
}
```

### 获取文件访问 URL

```bash
curl -X GET http://localhost:8089/api/v1/files/{fileId}/url \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 删除文件

```bash
curl -X DELETE http://localhost:8089/api/v1/upload/{fileId} \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

完整 API 文档请参考 [API.md](API.md)

## X-App-Id 使用规范

### 必需性

所有 API 请求**必须**包含 `X-App-Id` 请求头，否则返回 400 错误：

```json
{
  "code": 400,
  "message": "X-App-Id header is required",
  "data": null
}
```

### 格式要求

- 只能包含小写字母、数字、下划线、连字符
- 长度不超过 32 字符
- 正则表达式: `^[a-z0-9_-]+$`

**有效示例**:
- `blog`
- `im`
- `mobile-app`
- `admin_portal`

**无效示例**:
- `Blog` (包含大写字母)
- `my app` (包含空格)
- `app@123` (包含特殊字符)

### 权限隔离

- 用户只能访问属于自己 `appId` 的文件
- 尝试访问其他应用的文件将返回 403 错误：

```json
{
  "code": 403,
  "errorCode": "ACCESS_DENIED",
  "message": "Access denied: file belongs to different app",
  "data": null
}
```

## 错误处理

### 常见错误码

| HTTP 状态码 | 错误码 | 说明 | 解决方案 |
|------------|--------|------|----------|
| 400 | MISSING_REQUEST_HEADER | 缺少 X-App-Id | 添加 X-App-Id 请求头 |
| 400 | VALIDATION_ERROR | 请求参数无效 | 检查请求参数格式 |
| 400 | UNSUPPORTED_ACCESS_LEVEL | 访问级别非法 | 仅使用 `public` 或 `private` |
| 400 | EXTENSION_NOT_ALLOWED / CONTENT_TYPE_NOT_ALLOWED | 文件类型不允许 | 检查扩展名、Content-Type 与白名单配置 |
| 403 | ACCESS_DENIED | 权限不足 | 确认文件属于当前应用 |
| 404 | FILE_NOT_FOUND | 文件不存在 | 检查文件 ID 是否正确 |
| 404 | UPLOAD_TASK_NOT_FOUND | 上传任务不存在 | 检查 taskId 是否正确或任务是否已清理 |
| 413 | FILE_TOO_LARGE | 文件过大 | 减小文件大小或使用分片上传 |
| 500 | FILE_UPLOAD_FAILED / S3_CLIENT_ERROR | 存储上传失败 | 检查对象存储服务状态和网络连通性 |

### 错误响应示例

```json
{
  "code": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "Invalid X-App-Id format",
  "data": null
}
```

## 配置说明

### 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/file_service
    username: postgres
    password: postgres
```

数据库迁移不依赖 Flyway。开发和部署环境使用 Docker 中的轻量迁移容器执行 `docker/migrations/sql/*.sql`。

### 时区配置

File Service 使用 UTC+8 (Asia/Shanghai) 作为系统时区，所有时间戳都以本地时间（UTC+8）存储和显示。

**数据库时区**:
- PostgreSQL 配置为 UTC+8 时区
- 所有时间戳列使用 `TIMESTAMP` 类型（不带时区信息）
- 时间戳值按 UTC+8 本地时间存储

**Java 应用时区**:
- 使用 `LocalDateTime` 处理时间戳
- 不进行时区转换，直接存储和读取本地时间
- 所有时间操作都基于 UTC+8 时区

**时间戳格式**:
```json
{
  "createdAt": "2026-01-23T14:30:00",
  "updatedAt": "2026-01-23T15:45:00"
}
```

**注意事项**:
- 所有时间戳都是 UTC+8 本地时间，不包含时区信息
- 如果需要在其他时区显示，客户端需要自行转换
- 数据库和应用必须保持相同的时区配置（UTC+8）
- 不要混用不同时区的时间戳

**时区配置验证**:
```bash
# 检查 PostgreSQL 时区
docker exec -it file-service-postgres psql -U postgres -d file_service -c "SHOW timezone;"

# 预期输出: Asia/Shanghai
```

### 双 Bucket S3 存储配置

系统使用双 Bucket 策略，需要配置公开桶和私有桶：

```yaml
storage:
  type: s3
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:9001}
    access-key: ${S3_ACCESS_KEY:fileservice}
    secret-key: ${S3_SECRET_KEY:fileservice123}
    public-bucket: platform-files-public    # 公开存储桶
    private-bucket: platform-files-private  # 私有存储桶
    region: us-east-1
    cdn-domain: ${CDN_DOMAIN:}              # 可选：CDN 域名
  
  presigned:
    private-url-expire-hours: 1             # 私有文件预签名 URL 过期时间（小时）
    upload-url-expire-minutes: 15           # 上传预签名 URL 过期时间（分钟）
```

**配置说明**:
- `public-bucket`: 存储公开文件的桶名称
- `private-bucket`: 存储私有文件的桶名称
- `cdn-domain`: 可选的 CDN 域名，用于加速公开文件访问
- `private-url-expire-hours`: 私有文件访问 URL 的有效期
- `upload-url-expire-minutes`: 预签名上传 URL 的有效期

**初始化存储桶**:

使用 MinIO 客户端或 AWS CLI 创建存储桶：

```bash
# 使用 MinIO 客户端
mc mb minio/platform-files-public
mc mb minio/platform-files-private

# 设置公开桶的访问策略
mc anonymous set download minio/platform-files-public

# 或使用 AWS CLI
aws s3 mb s3://platform-files-public --endpoint-url http://localhost:9001
aws s3 mb s3://platform-files-private --endpoint-url http://localhost:9001
```

### 租户管理配置

```yaml
tenant:
  defaults:
    max-storage-bytes: 10737418240     # 默认最大存储空间：10GB
    max-file-count: 10000              # 默认最大文件数量：10000
    max-single-file-size: 104857600    # 默认单文件大小限制：100MB
  auto-create: true                    # 是否自动创建未知租户
```

**配置说明**:
- `defaults.max-storage-bytes`: 新租户的默认存储空间配额（字节）
- `defaults.max-file-count`: 新租户的默认文件数量配额
- `defaults.max-single-file-size`: 新租户的默认单文件大小限制（字节）
- `auto-create`: 是否自动创建未知租户
  - `true`: 遇到未知租户 ID 时自动创建，使用默认配额
  - `false`: 遇到未知租户 ID 时返回错误

**租户配额建议**:
- 小型应用: 10GB 存储，10000 文件
- 中型应用: 50GB 存储，50000 文件
- 大型应用: 100GB+ 存储，100000+ 文件

### 管理员 API Key 配置

管理员 API 使用 API Key 进行认证：

```yaml
admin:
  api-keys:
    - name: admin-console          # API Key 名称（用于标识）
      key: ${ADMIN_API_KEY}        # API Key 值（从环境变量读取）
      permissions: [READ, WRITE, DELETE]
    - name: monitoring             # 只读监控 Key
      key: ${MONITORING_API_KEY}
      permissions: [READ]
```

**环境变量配置**:

```bash
# 生产环境
export ADMIN_API_KEY=your-secure-admin-key-here-at-least-32-chars
export MONITORING_API_KEY=your-monitoring-key-here-at-least-32-chars

# 开发环境（示例）
export ADMIN_API_KEY=dev-admin-key-12345678901234567890
export MONITORING_API_KEY=dev-monitoring-key-12345678901234567890
```

**安全建议**:
- 使用强随机字符串作为 API Key（至少 32 字符）
- 不要在代码中硬编码 API Key
- 定期轮换 API Key（建议每 90 天）
- 为不同用途创建不同的 API Key
- 限制 API Key 的权限范围
- 在生产环境使用环境变量或密钥管理服务

**生成安全的 API Key**:

```bash
# Linux/Mac
openssl rand -hex 32

# PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

### 上传限制配置

```yaml
upload:
  max-file-size: 10485760      # 10MB
  max-image-size: 5242880      # 5MB
  allowed-image-types:
    - image/jpeg
    - image/png
    - image/webp
  allowed-file-types:
    - application/pdf
    - application/msword
```

## 监控与运维

### 健康检查

```bash
curl http://localhost:8089/actuator/health
```

### 指标监控

```bash
curl http://localhost:8089/actuator/metrics
```

### 日志级别

```yaml
logging:
  level:
    com.platform.file: DEBUG
```

## 架构设计

File Service 采用 DDD 分层架构：

```
file-service/
├── domain/           # 领域层 - 核心业务逻辑
├── application/      # 应用层 - 用例编排
├── infrastructure/   # 基础设施层 - 技术实现
└── interfaces/       # 接口层 - API 暴露
```

详细设计文档请参考项目 `.kiro/specs/file-service-refactoring/design.md`

## 测试

### 单元测试

```bash
mvn test
```

### 集成测试

```bash
mvn verify -P integration-test
```

### API 测试

```powershell
cd tests/api/file
.\test-file-service-api.ps1
```

## 常见问题

### Q: 为什么需要双 Bucket 策略？

A: 双 Bucket 策略可以更好地管理文件访问权限。公开文件存储在公开桶中，可以直接访问，提高访问速度；私有文件存储在私有桶中，通过预签名 URL 访问，保证安全性。

### Q: 如何选择文件的访问级别？

A: 
- 使用 `PUBLIC` 访问级别：适用于公开内容，如博客文章图片、公开文档等
- 使用 `PRIVATE` 访问级别：适用于私密内容，如聊天文件、私人文档等

### Q: 租户配额超限后会怎样？

A: 当租户的存储空间或文件数量达到配额限制时，新的上传请求会被拒绝，返回 413 错误（配额超限）。管理员可以通过管理员 API 调整租户配额。

### Q: 如何管理租户？

A: 使用管理员 API 进行租户管理：
- 创建租户: `POST /api/v1/admin/tenants`
- 查询租户: `GET /api/v1/admin/tenants`
- 更新配额: `PUT /api/v1/admin/tenants/{tenantId}`
- 停用租户: `PUT /api/v1/admin/tenants/{tenantId}/status`

所有管理员 API 需要在请求头中携带 `X-Admin-Api-Key`。

### Q: 为什么需要 X-App-Id？

A: X-App-Id 用于实现多租户隔离，确保不同应用的文件数据相互独立，提高安全性和可维护性。

### Q: 可以不传 X-App-Id 吗？

A: 不可以。所有 API 请求都必须携带 X-App-Id，这是强制要求。

### Q: 如何添加新的应用 ID？

A: 
- 如果启用了租户自动创建（`tenant.auto-create: true`），只需在调用 API 时传递新的 appId 即可，系统会自动创建租户并使用默认配额
- 如果禁用了自动创建，需要先通过管理员 API 创建租户

### Q: 文件去重是如何工作的？

A: 同一应用内，系统会计算文件的 MD5 哈希值。如果哈希值相同，则复用已存在的物理文件，只创建新的文件记录。

### Q: 删除文件会立即删除物理存储吗？

A: 不会。系统采用引用计数机制，只有当所有引用都被删除后，才会删除物理文件。

### Q: 管理员 API Key 如何管理？

A: 
- API Key 在 `application.yml` 中配置，建议使用环境变量
- 定期轮换 API Key（建议每 90 天）
- 为不同用途创建不同的 API Key
- 使用强随机字符串（至少 32 字符）

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License

## 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issue Tracker]
- 技术文档: [Wiki]
