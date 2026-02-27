# 分层架构

## 包结构总览

```
com.architectcgz.file/
├── interfaces/                # 接口层 — 处理 HTTP 请求，参数校验，响应封装
│   └── controller/
├── application/               # 应用服务层 — 编排业务流程，事务管理
│   └── service/
├── domain/                    # 领域层 — 核心业务模型与仓储接口
│   ├── model/
│   └── repository/
├── infrastructure/            # 基础设施层 — 技术实现细节
│   ├── storage/               # 对象存储（S3/本地）
│   ├── repository/            # 仓储实现 + MyBatis Mapper + PO
│   ├── config/                # 配置属性类
│   ├── image/                 # 图片处理
│   ├── cache/                 # 缓存
│   ├── filter/                # HTTP 过滤器
│   ├── interceptor/           # 拦截器
│   ├── scheduler/             # 定时任务
│   ├── monitoring/            # 监控指标
│   └── util/                  # 工具类
└── common/                    # 通用层 — 跨层共享
    ├── context/               # 用户上下文
    ├── exception/             # 自定义异常
    ├── result/                # 统一响应
    └── util/                  # 通用工具
```

## 各层职责

### 接口层 (interfaces)

负责 HTTP 协议适配，包含 7 个 Controller：

| Controller | 路径前缀 | 职责 |
|------------|----------|------|
| UploadController | `/api/v1/upload` | 基础上传（图片/文件）、删除 |
| FileController | `/api/v1/files` | 文件访问 URL、详情、权限变更 |
| MultipartController | `/api/v1/multipart` | 分片上传全流程 |
| DirectUploadController | `/api/v1/direct-upload` | 客户端直传 S3 |
| PresignedController | `/api/v1/upload` | 预签名 URL 上传/确认 |
| FileAdminController | `/api/v1/admin/files` | 管理员文件管理 |
| TenantAdminController | `/api/v1/admin/tenants` | 租户管理 |

### 应用服务层 (application)

编排业务流程，管理事务边界。包含 10 个 Service：

| Service | 职责 |
|---------|------|
| UploadApplicationService | 基础上传逻辑（图片处理、去重、存储） |
| MultipartUploadService | 分片上传（初始化、上传分片、完成、中止） |
| DirectUploadService | 客户端直传（预签名 URL 分发） |
| InstantUploadService | 秒传检测（基于文件哈希） |
| PresignedUrlService | 预签名 URL 生成与上传确认 |
| FileAccessService | 文件访问控制（URL 生成、权限验证） |
| FileManagementService | 文件管理（查询、统计） |
| FileTypeValidator | 文件类型/大小校验 |
| AuditLogService | 审计日志记录与查询 |
| TenantManagementService | 租户 CRUD 与配额管理 |
