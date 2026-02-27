# API 接口清单

## 基础上传 (UploadController)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/upload/image` | 上传图片（支持压缩、WebP 转换、缩略图） |
| POST | `/api/v1/upload/file` | 上传文件 |
| DELETE | `/api/v1/upload/{fileRecordId}` | 删除文件（软删除） |

## 文件访问 (FileController)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/files/{fileId}/url` | 获取文件访问 URL |
| GET | `/api/v1/files/{fileId}` | 获取文件详情 |
| PUT | `/api/v1/files/{fileId}/access-level` | 修改访问级别（PUBLIC/PRIVATE） |

## 分片上传 (MultipartController)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/multipart/init` | 初始化分片上传（支持断点续传检查） |
| PUT | `/api/v1/multipart/{taskId}/parts/{partNumber}` | 上传单个分片 |
| POST | `/api/v1/multipart/{taskId}/complete` | 完成分片上传 |
| DELETE | `/api/v1/multipart/{taskId}` | 中止分片上传 |
| GET | `/api/v1/multipart/{taskId}/progress` | 查询上传进度 |
| GET | `/api/v1/multipart/tasks` | 列出用户上传任务 |

## 客户端直传 (DirectUploadController)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/direct-upload/init` | 初始化直传（含秒传/断点续传检查） |
| POST | `/api/v1/direct-upload/part-urls` | 获取分片预签名 URL |
| POST | `/api/v1/direct-upload/{taskId}/complete` | 完成直传上传 |

## 预签名 URL (PresignedController)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/upload/presign` | 获取预签名上传 URL |
| POST | `/api/v1/upload/confirm` | 确认上传完成 |

## 管理员 - 文件管理 (FileAdminController)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/files` | 查询文件列表（多条件过滤） |
| GET | `/api/v1/admin/files/{fileId}` | 查询文件详情 |
| DELETE | `/api/v1/admin/files/{fileId}` | 删除文件 |
| POST | `/api/v1/admin/files/batch-delete` | 批量删除文件 |
| GET | `/api/v1/admin/files/statistics` | 获取存储统计 |
| GET | `/api/v1/admin/files/statistics/by-tenant` | 按租户获取统计 |

## 管理员 - 租户管理 (TenantAdminController)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/admin/tenants` | 创建租户 |
| GET | `/api/v1/admin/tenants` | 查询租户列表 |
| GET | `/api/v1/admin/tenants/{tenantId}` | 查询租户详情 |
| PUT | `/api/v1/admin/tenants/{tenantId}` | 更新租户 |
| PUT | `/api/v1/admin/tenants/{tenantId}/status` | 更新租户状态 |
| DELETE | `/api/v1/admin/tenants/{tenantId}` | 删除租户（软删除） |

## 通用请求头

| Header | 说明 | 必填 |
|--------|------|------|
| X-App-Id | 租户/应用标识 | 是 |
| X-User-Id | 用户标识 | 是 |

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```
