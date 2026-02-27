# File Service API 文档

## 概述

File Service 提供完整的文件管理 API，支持多种上传方式和文件操作。

**Base URL**: `http://localhost:8089`

**认证方式**: JWT Bearer Token

**必需请求头**:
```http
X-App-Id: {your-app-id}
Authorization: Bearer {your-jwt-token}
```

## 通用说明

### 请求头

所有 API 请求必须包含以下请求头：

| 请求头 | 必需 | 说明 | 示例 |
|--------|------|------|------|
| X-App-Id | 是 | 应用标识符 | `blog`, `im` |
| Authorization | 是 | JWT Token | `Bearer eyJhbGc...` |
| Content-Type | 部分 | 内容类型 | `multipart/form-data`, `application/json` |

### 响应格式

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    // 响应数据
  }
}
```

#### 错误响应

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

### 错误码

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 200 | 200 | 成功 |
| 400 | 400 | 请求参数错误 |
| 401 | 401 | 未认证或 Token 无效 |
| 403 | 403 | 权限不足 |
| 404 | 404 | 资源不存在 |
| 413 | 413 | 文件过大 |
| 500 | 500 | 服务器内部错误 |

---

## 1. 文件上传 API

### 1.1 上传图片

上传图片文件，自动进行图片处理（压缩、格式转换等）。

**端点**: `POST /api/v1/upload/image`

**请求头**:
```http
Content-Type: multipart/form-data
X-App-Id: blog
Authorization: Bearer {token}
```

**请求参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| file | File | 是 | 图片文件 |

**支持的图片格式**:
- `image/jpeg`
- `image/png`
- `image/webp`
- `image/gif`

**文件大小限制**: 5MB

**请求示例**:

```bash
curl -X POST http://localhost:8089/api/v1/upload/image \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "file=@photo.jpg"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/01JGXXX-XXX-XXX.webp",
    "originalName": "photo.jpg",
    "fileSize": 102400,
    "contentType": "image/webp"
  }
}
```

---

### 1.2 上传文件

上传普通文件（文档、压缩包等）。

**端点**: `POST /api/v1/upload/file`

**请求头**:
```http
Content-Type: multipart/form-data
X-App-Id: im
Authorization: Bearer {token}
```

**请求参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| file | File | 是 | 文件 |

**支持的文件格式**:
- `application/pdf`
- `application/msword`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `application/zip`
- `text/plain`

**文件大小限制**: 10MB

**请求示例**:

```bash
curl -X POST http://localhost:8089/api/v1/upload/file \
  -H "X-App-Id: im" \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "file=@document.pdf"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGYYY-YYY-YYY-YYY-YYYYYYYYYYYY",
    "url": "https://cdn.example.com/im/2026/01/19/67890/files/01JGYYY-YYY-YYY.pdf",
    "originalName": "document.pdf",
    "fileSize": 204800,
    "contentType": "application/pdf"
  }
}
```

---

## 2. 文件访问 API

### 2.1 获取文件访问 URL

获取文件的访问 URL（可能是预签名 URL）。

**端点**: `GET /api/v1/files/{fileId}/url`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/files/01JGXXX-XXX-XXX/url \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..."
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/xxx.webp?X-Amz-Expires=3600",
    "expiresAt": "2026-01-19T12:00:00Z"
  }
}
```

---

### 2.2 获取文件详情

获取文件的详细信息。

**端点**: `GET /api/v1/files/{fileId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/files/01JGXXX-XXX-XXX \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..."
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "originalName": "photo.jpg",
    "fileSize": 102400,
    "contentType": "image/webp",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/xxx.webp",
    "createdAt": "2026-01-19T10:00:00Z",
    "status": "active",
    "accessLevel": "public"
  }
}
```

---

## 3. 文件删除 API

### 3.1 删除文件

删除指定文件（软删除）。

**端点**: `DELETE /api/v1/upload/{fileId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**请求示例**:

```bash
curl -X DELETE http://localhost:8089/api/v1/upload/01JGXXX-XXX-XXX \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..."
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**注意事项**:
- 只能删除属于当前 `appId` 的文件
- 删除操作是软删除，文件记录标记为已删除
- 物理文件采用引用计数，只有当所有引用删除后才会删除物理存储

---

## 4. 分片上传 API

### 4.1 初始化分片上传

初始化一个分片上传任务。

**端点**: `POST /api/v1/multipart/init`

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

```json
{
  "fileName": "large-video.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "chunkSize": 5242880
}
```

**请求参数说明**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileName | String | 是 | 文件名 |
| fileSize | Long | 是 | 文件总大小（字节） |
| contentType | String | 是 | MIME 类型 |
| chunkSize | Long | 是 | 分片大小（字节） |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "uploadId": "s3-upload-id-xxx",
    "totalChunks": 20,
    "chunkSize": 5242880
  }
}
```

---

### 4.2 上传分片

上传单个分片。

**端点**: `POST /api/v1/multipart/{taskId}/upload`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |

**查询参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| partNumber | Integer | 是 | 分片编号（从 1 开始） |

**请求头**:
```http
Content-Type: multipart/form-data
X-App-Id: blog
Authorization: Bearer {token}
```

**请求参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| file | File | 是 | 分片数据 |

**请求示例**:

```bash
curl -X POST "http://localhost:8089/api/v1/multipart/01JGZZZ-ZZZ-ZZZ/upload?partNumber=1" \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "file=@chunk-1.bin"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "partNumber": 1,
    "etag": "\"d41d8cd98f00b204e9800998ecf8427e\""
  }
}
```

---

### 4.3 完成分片上传

完成分片上传，合并所有分片。

**端点**: `POST /api/v1/multipart/{taskId}/complete`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

```json
{
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/videos/xxx.mp4",
    "originalName": "large-video.mp4",
    "fileSize": 104857600,
    "contentType": "video/mp4"
  }
}
```

---

### 4.4 取消分片上传

取消分片上传任务。

**端点**: `DELETE /api/v1/multipart/{taskId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 5. 预签名 URL 上传 API

### 5.1 获取预签名上传 URL

获取预签名 URL，客户端可直接上传到 S3。

**端点**: `POST /api/v1/presigned/upload-url`

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

```json
{
  "fileName": "photo.jpg",
  "fileSize": 102400,
  "contentType": "image/jpeg"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "uploadUrl": "https://s3.amazonaws.com/bucket/path?X-Amz-Signature=...",
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "expiresAt": "2026-01-19T11:00:00Z",
    "headers": {
      "Content-Type": "image/jpeg"
    }
  }
}
```

---

### 5.2 确认预签名上传

确认文件已上传完成。

**端点**: `POST /api/v1/presigned/{fileId}/confirm`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

```json
{
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/xxx.jpg",
    "status": "active"
  }
}
```

---

## 6. 秒传 API

### 6.1 检查文件是否存在

检查文件是否已存在（基于 MD5 哈希）。

**端点**: `POST /api/v1/instant-upload/check`

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

```json
{
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
  "fileName": "photo.jpg",
  "fileSize": 102400,
  "contentType": "image/jpeg"
}
```

**响应示例（文件存在）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "exists": true,
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/images/xxx.jpg"
  }
}
```

**响应示例（文件不存在）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "exists": false
  }
}
```

---

## 7. X-App-Id 错误处理

### 7.1 缺少 X-App-Id

**请求**:
```bash
curl -X POST http://localhost:8089/api/v1/upload/image \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "file=@photo.jpg"
```

**响应**:
```json
{
  "code": 400,
  "message": "X-App-Id header is required",
  "data": null
}
```

---

### 7.2 无效的 X-App-Id 格式

**请求**:
```bash
curl -X POST http://localhost:8089/api/v1/upload/image \
  -H "X-App-Id: Invalid@AppId" \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "file=@photo.jpg"
```

**响应**:
```json
{
  "code": 400,
  "message": "Invalid X-App-Id format",
  "data": null
}
```

---

### 7.3 跨应用访问文件

**请求**:
```bash
# 文件属于 blog 应用，但使用 im 应用 ID 访问
curl -X GET http://localhost:8089/api/v1/files/01JGXXX-XXX-XXX \
  -H "X-App-Id: im" \
  -H "Authorization: Bearer eyJhbGc..."
```

**响应**:
```json
{
  "code": 403,
  "message": "Access denied: file belongs to different app",
  "data": null
}
```

---

## 8. 使用示例

### 8.1 Blog 应用上传文章封面

```javascript
// JavaScript 示例
const formData = new FormData();
formData.append('file', coverImageFile);

const response = await fetch('http://localhost:8089/api/v1/upload/image', {
  method: 'POST',
  headers: {
    'X-App-Id': 'blog',
    'Authorization': `Bearer ${token}`
  },
  body: formData
});

const result = await response.json();
console.log('Cover URL:', result.data.url);
```

---

### 8.2 IM 应用发送文件消息

```java
// Java 示例
@Service
public class MessageService {
    
    @Autowired
    private FileServiceClient fileServiceClient;
    
    public String sendFileMessage(MultipartFile file, Long receiverId) {
        // 上传文件
        ApiResponse<FileUploadResponse> response = 
            fileServiceClient.uploadFile(FileConstants.APP_ID_IM, file);
        
        if (response.getCode() != 200) {
            throw new RuntimeException("File upload failed");
        }
        
        String fileUrl = response.getData().getUrl();
        
        // 发送消息
        sendMessage(receiverId, fileUrl);
        
        return fileUrl;
    }
}
```

---

## 9. 最佳实践

### 9.1 选择合适的上传方式

| 场景 | 推荐方式 | 原因 |
|------|----------|------|
| 小文件 (<5MB) | 直接上传 | 简单快速 |
| 大文件 (>10MB) | 分片上传 | 支持断点续传 |
| 客户端直传 | 预签名 URL | 减轻服务器压力 |
| 重复文件 | 秒传 | 节省带宽和时间 |

### 9.2 错误重试策略

```javascript
async function uploadWithRetry(file, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await uploadFile(file);
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      await sleep(1000 * Math.pow(2, i)); // 指数退避
    }
  }
}
```

### 9.3 文件哈希计算

```javascript
// 客户端计算 MD5
async function calculateMD5(file) {
  const buffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('MD5', buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}
```

---

## 10. 限流与配额

### 10.1 请求限流

- 每个 IP 每秒最多 10 个请求
- 超过限制返回 429 Too Many Requests

### 10.2 存储配额

- 每个应用默认配额: 100GB
- 超过配额后上传失败，返回 507 Insufficient Storage

---

---

## 11. 管理员 API - 租户管理

管理员 API 需要使用 API Key 进行认证。所有管理员 API 请求必须包含 `X-Admin-Api-Key` 请求头。

### 11.1 认证说明

**认证方式**: API Key

**必需请求头**:
```http
X-Admin-Api-Key: {your-admin-api-key}
Content-Type: application/json
```

**错误响应（未认证）**:
```json
{
  "code": 401,
  "message": "Invalid or missing API key",
  "data": null
}
```

---

### 11.2 创建租户

创建新的租户。

**端点**: `POST /api/v1/admin/tenants`

**请求头**:
```http
Content-Type: application/json
X-Admin-Api-Key: {admin-api-key}
```

**请求体**:

```json
{
  "tenantId": "blog",
  "tenantName": "Blog Application",
  "maxStorageBytes": 10737418240,
  "maxFileCount": 10000,
  "maxSingleFileSize": 104857600,
  "allowedFileTypes": ["image/jpeg", "image/png", "application/pdf"],
  "contactEmail": "admin@blog.com"
}
```

**请求参数说明**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 是 | 租户 ID（唯一标识） |
| tenantName | String | 是 | 租户名称 |
| maxStorageBytes | Long | 否 | 最大存储空间（字节），默认 10GB |
| maxFileCount | Integer | 否 | 最大文件数量，默认 10000 |
| maxSingleFileSize | Long | 否 | 单文件大小限制（字节），默认 100MB |
| allowedFileTypes | String[] | 否 | 允许的文件类型列表 |
| contactEmail | String | 否 | 联系邮箱 |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tenantId": "blog",
    "tenantName": "Blog Application",
    "status": "ACTIVE",
    "maxStorageBytes": 10737418240,
    "maxFileCount": 10000,
    "maxSingleFileSize": 104857600,
    "allowedFileTypes": ["image/jpeg", "image/png", "application/pdf"],
    "contactEmail": "admin@blog.com",
    "createdAt": "2026-01-21T10:00:00Z",
    "updatedAt": "2026-01-21T10:00:00Z"
  }
}
```

---

### 11.3 查询租户列表

查询租户列表，支持分页和状态过滤。

**端点**: `GET /api/v1/admin/tenants`

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**查询参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| status | String | 否 | 租户状态过滤（ACTIVE, SUSPENDED, DELETED） |
| page | Integer | 否 | 页码，从 0 开始，默认 0 |
| size | Integer | 否 | 每页大小，默认 20 |

**请求示例**:

```bash
curl -X GET "http://localhost:8089/api/v1/admin/tenants?status=ACTIVE&page=0&size=20" \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "tenantId": "blog",
        "tenantName": "Blog Application",
        "status": "ACTIVE",
        "maxStorageBytes": 10737418240,
        "maxFileCount": 10000,
        "createdAt": "2026-01-21T10:00:00Z"
      },
      {
        "tenantId": "im",
        "tenantName": "IM Application",
        "status": "ACTIVE",
        "maxStorageBytes": 21474836480,
        "maxFileCount": 50000,
        "createdAt": "2026-01-20T09:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

---

### 11.4 查询租户详情

查询单个租户的详细信息，包含使用统计。

**端点**: `GET /api/v1/admin/tenants/{tenantId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 是 | 租户 ID |

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/admin/tenants/blog \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tenant": {
      "tenantId": "blog",
      "tenantName": "Blog Application",
      "status": "ACTIVE",
      "maxStorageBytes": 10737418240,
      "maxFileCount": 10000,
      "maxSingleFileSize": 104857600,
      "allowedFileTypes": ["image/jpeg", "image/png", "application/pdf"],
      "contactEmail": "admin@blog.com",
      "createdAt": "2026-01-21T10:00:00Z",
      "updatedAt": "2026-01-21T10:00:00Z"
    },
    "usage": {
      "usedStorageBytes": 5368709120,
      "usedFileCount": 4523,
      "storageUsagePercent": 50.0,
      "fileCountUsagePercent": 45.23,
      "lastUploadAt": "2026-01-21T09:30:00Z"
    }
  }
}
```

---

### 11.5 更新租户配置

更新租户的配额和联系信息。

**端点**: `PUT /api/v1/admin/tenants/{tenantId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 是 | 租户 ID |

**请求头**:
```http
Content-Type: application/json
X-Admin-Api-Key: {admin-api-key}
```

**请求体**:

```json
{
  "tenantName": "Blog Application Updated",
  "maxStorageBytes": 21474836480,
  "maxFileCount": 20000,
  "maxSingleFileSize": 209715200,
  "allowedFileTypes": ["image/jpeg", "image/png", "image/webp", "application/pdf"],
  "contactEmail": "newadmin@blog.com"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tenantId": "blog",
    "tenantName": "Blog Application Updated",
    "status": "ACTIVE",
    "maxStorageBytes": 21474836480,
    "maxFileCount": 20000,
    "maxSingleFileSize": 209715200,
    "allowedFileTypes": ["image/jpeg", "image/png", "image/webp", "application/pdf"],
    "contactEmail": "newadmin@blog.com",
    "createdAt": "2026-01-21T10:00:00Z",
    "updatedAt": "2026-01-21T11:00:00Z"
  }
}
```

---

### 11.6 更新租户状态

更新租户状态（启用/停用）。

**端点**: `PUT /api/v1/admin/tenants/{tenantId}/status`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 是 | 租户 ID |

**请求头**:
```http
Content-Type: application/json
X-Admin-Api-Key: {admin-api-key}
```

**请求体**:

```json
{
  "status": "SUSPENDED"
}
```

**请求参数说明**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| status | String | 是 | 新状态（ACTIVE 或 SUSPENDED） |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**注意事项**:
- 停用租户后，该租户的所有上传操作将被拒绝
- 已上传的文件仍然可以访问
- 可以通过设置状态为 ACTIVE 重新启用租户

---

### 11.7 删除租户

删除租户（软删除）。

**端点**: `DELETE /api/v1/admin/tenants/{tenantId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 是 | 租户 ID |

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X DELETE http://localhost:8089/api/v1/admin/tenants/blog \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**注意事项**:
- 删除操作是软删除，租户状态标记为 DELETED
- 租户的历史数据保留用于审计
- 已删除的租户无法恢复，但数据不会被物理删除

---

## 12. 管理员 API - 文件管理

### 12.1 查询文件列表

查询文件列表，支持多种过滤条件和分页。

**端点**: `GET /api/v1/admin/files`

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**查询参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| tenantId | String | 否 | 租户 ID 过滤 |
| userId | Long | 否 | 用户 ID 过滤 |
| contentType | String | 否 | 内容类型过滤 |
| accessLevel | String | 否 | 访问级别过滤（PUBLIC, PRIVATE） |
| startTime | String | 否 | 开始时间（ISO 8601 格式） |
| endTime | String | 否 | 结束时间（ISO 8601 格式） |
| minSize | Long | 否 | 最小文件大小（字节） |
| maxSize | Long | 否 | 最大文件大小（字节） |
| page | Integer | 否 | 页码，从 0 开始，默认 0 |
| size | Integer | 否 | 每页大小，默认 20 |
| sortBy | String | 否 | 排序字段，默认 createdAt |
| sortOrder | String | 否 | 排序方向（asc, desc），默认 desc |

**请求示例**:

```bash
curl -X GET "http://localhost:8089/api/v1/admin/files?tenantId=blog&accessLevel=PUBLIC&page=0&size=20" \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
        "tenantId": "blog",
        "userId": 12345,
        "originalName": "photo.jpg",
        "fileSize": 102400,
        "contentType": "image/webp",
        "accessLevel": "PUBLIC",
        "storagePath": "blog/2026/01/21/12345/images/xxx.webp",
        "createdAt": "2026-01-21T10:00:00Z",
        "status": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 12.2 查询文件详情

查询单个文件的详细信息。

**端点**: `GET /api/v1/admin/files/{fileId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/admin/files/01JGXXX-XXX-XXX \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "tenantId": "blog",
    "userId": 12345,
    "originalName": "photo.jpg",
    "fileSize": 102400,
    "contentType": "image/webp",
    "accessLevel": "PUBLIC",
    "storagePath": "blog/2026/01/21/12345/images/xxx.webp",
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "createdAt": "2026-01-21T10:00:00Z",
    "updatedAt": "2026-01-21T10:00:00Z",
    "status": "ACTIVE"
  }
}
```

---

### 12.3 删除文件

删除指定文件（物理删除）。

**端点**: `DELETE /api/v1/admin/files/{fileId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileId | String | 是 | 文件 ID |

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X DELETE http://localhost:8089/api/v1/admin/files/01JGXXX-XXX-XXX \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**注意事项**:
- 管理员删除是物理删除，会从存储系统和数据库中删除文件
- 删除操作会更新租户的使用统计
- 删除操作会记录审计日志

---

### 12.4 批量删除文件

批量删除多个文件。

**端点**: `POST /api/v1/admin/files/batch-delete`

**请求头**:
```http
Content-Type: application/json
X-Admin-Api-Key: {admin-api-key}
```

**请求体**:

```json
{
  "fileIds": [
    "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "01JGYYY-YYY-YYY-YYY-YYYYYYYYYYYY",
    "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ"
  ]
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalRequested": 3,
    "successCount": 2,
    "failureCount": 1,
    "failures": [
      {
        "fileId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
        "reason": "File not found"
      }
    ]
  }
}
```

**注意事项**:
- 批量删除会尝试删除所有指定的文件
- 即使某些文件删除失败，也会继续删除其他文件
- 返回详细的成功和失败信息

---

### 12.5 获取存储统计

获取全局存储统计信息。

**端点**: `GET /api/v1/admin/files/statistics`

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/admin/files/statistics \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalFiles": 15234,
    "totalStorageBytes": 53687091200,
    "publicFiles": 8456,
    "privateFiles": 6778,
    "filesByType": {
      "image/jpeg": 5234,
      "image/png": 3222,
      "application/pdf": 2456,
      "video/mp4": 1234,
      "other": 3088
    },
    "storageByTenant": {
      "blog": 21474836480,
      "im": 32212254720
    },
    "statisticsTime": "2026-01-21T11:00:00Z"
  }
}
```

---

### 12.6 按租户获取存储统计

获取每个租户的存储使用情况。

**端点**: `GET /api/v1/admin/files/statistics/by-tenant`

**请求头**:
```http
X-Admin-Api-Key: {admin-api-key}
```

**请求示例**:

```bash
curl -X GET http://localhost:8089/api/v1/admin/files/statistics/by-tenant \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "blog": {
      "tenantId": "blog",
      "tenantName": "Blog Application",
      "fileCount": 4523,
      "storageBytes": 21474836480,
      "maxStorageBytes": 21474836480,
      "maxFileCount": 20000,
      "storageUsagePercent": 100.0,
      "fileCountUsagePercent": 22.62
    },
    "im": {
      "tenantId": "im",
      "tenantName": "IM Application",
      "fileCount": 10711,
      "storageBytes": 32212254720,
      "maxStorageBytes": 53687091200,
      "maxFileCount": 50000,
      "storageUsagePercent": 60.0,
      "fileCountUsagePercent": 21.42
    }
  }
}
```

---

## 13. 错误码扩展

### 13.1 租户相关错误

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 404 | TENANT_NOT_FOUND | 租户不存在 |
| 403 | TENANT_SUSPENDED | 租户已被停用 |
| 413 | QUOTA_EXCEEDED | 配额超限 |
| 413 | FILE_TOO_LARGE | 文件过大 |

**错误响应示例（租户停用）**:
```json
{
  "code": 403,
  "message": "Tenant is suspended",
  "data": {
    "tenantId": "blog",
    "status": "SUSPENDED"
  }
}
```

**错误响应示例（配额超限）**:
```json
{
  "code": 413,
  "message": "Storage quota exceeded",
  "data": {
    "tenantId": "blog",
    "currentUsage": 10737418240,
    "limit": 10737418240,
    "quotaType": "STORAGE_QUOTA"
  }
}
```

---

## 14. 管理员 API 使用示例

### 14.1 创建租户并配置配额

```bash
# 创建租户
curl -X POST http://localhost:8089/api/v1/admin/tenants \
  -H "Content-Type: application/json" \
  -H "X-Admin-Api-Key: your-admin-api-key" \
  -d '{
    "tenantId": "new-app",
    "tenantName": "New Application",
    "maxStorageBytes": 10737418240,
    "maxFileCount": 10000,
    "maxSingleFileSize": 104857600,
    "contactEmail": "admin@newapp.com"
  }'
```

### 14.2 监控租户使用情况

```bash
# 查询租户详情
curl -X GET http://localhost:8089/api/v1/admin/tenants/blog \
  -H "X-Admin-Api-Key: your-admin-api-key"

# 查看存储统计
curl -X GET http://localhost:8089/api/v1/admin/files/statistics/by-tenant \
  -H "X-Admin-Api-Key: your-admin-api-key"
```

### 14.3 停用超限租户

```bash
# 停用租户
curl -X PUT http://localhost:8089/api/v1/admin/tenants/blog/status \
  -H "Content-Type: application/json" \
  -H "X-Admin-Api-Key: your-admin-api-key" \
  -d '{
    "status": "SUSPENDED"
  }'
```

### 14.4 清理文件

```bash
# 查询特定租户的文件
curl -X GET "http://localhost:8089/api/v1/admin/files?tenantId=blog&page=0&size=100" \
  -H "X-Admin-Api-Key: your-admin-api-key"

# 批量删除文件
curl -X POST http://localhost:8089/api/v1/admin/files/batch-delete \
  -H "Content-Type: application/json" \
  -H "X-Admin-Api-Key: your-admin-api-key" \
  -d '{
    "fileIds": ["file-id-1", "file-id-2", "file-id-3"]
  }'
```

---

## 附录

### A. 支持的文件类型

#### 图片类型
- `image/jpeg` (.jpg, .jpeg)
- `image/png` (.png)
- `image/webp` (.webp)
- `image/gif` (.gif)

#### 文档类型
- `application/pdf` (.pdf)
- `application/msword` (.doc)
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (.docx)
- `text/plain` (.txt)

#### 压缩包类型
- `application/zip` (.zip)
- `application/x-rar-compressed` (.rar)

### B. 文件大小限制

| 文件类型 | 最大大小 |
|----------|----------|
| 图片 | 5MB |
| 文档 | 10MB |
| 视频 | 100MB (需使用分片上传) |

### C. 存储路径规则

```
/{bucket}/{tenantId}/{year}/{month}/{day}/{userId}/{type}/{uuid}.{ext}

- bucket: 存储桶（platform-files-public 或 platform-files-private）
- tenantId: 租户标识符（等同于 appId）
- year: 4位年份
- month: 2位月份
- day: 2位日期
- userId: 用户ID
- type: 文件类型目录 (images, files, videos, audios)
- uuid: UUIDv7 文件ID
- ext: 文件扩展名
```

**示例**:
```
公开文件: platform-files-public/blog/2026/01/21/12345/images/01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX.webp
私有文件: platform-files-private/im/2026/01/21/67890/files/01JGYYY-YYY-YYY-YYY-YYYYYYYYYYYY.pdf
```

### D. 双 Bucket 策略

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

### E. 管理员 API Key 配置

管理员 API 使用 API Key 进行认证。在 `application.yml` 中配置：

```yaml
admin:
  api-keys:
    - name: admin-console
      key: ${ADMIN_API_KEY}
      permissions: [READ, WRITE, DELETE]
    - name: monitoring
      key: ${MONITORING_API_KEY}
      permissions: [READ]
```

**环境变量配置**:
```bash
export ADMIN_API_KEY=your-secure-admin-key-here
export MONITORING_API_KEY=your-monitoring-key-here
```

**安全建议**:
- 使用强随机字符串作为 API Key（至少 32 字符）
- 不要在代码中硬编码 API Key
- 定期轮换 API Key
- 为不同用途创建不同的 API Key
- 限制 API Key 的权限范围
