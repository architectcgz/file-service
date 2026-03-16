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
  "errorCode": null,
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
  "errorCode": "VALIDATION_ERROR",
  "message": "错误描述",
  "data": null
}
```

### 错误码

`code` 为数值型状态码，兼容现有客户端；`errorCode` 为稳定字符串业务码，仅在错误响应中返回。

| HTTP 状态码 | errorCode | 说明 |
|------------|-----------|------|
| 400 | VALIDATION_ERROR | 请求参数校验失败 |
| 400 | MISSING_REQUEST_HEADER | 缺少必需请求头 |
| 400 | BUSINESS_ERROR | 通用业务异常兜底 |
| 403 | ACCESS_DENIED | 权限不足 |
| 404 | FILE_NOT_FOUND / TENANT_NOT_FOUND / UPLOAD_TASK_NOT_FOUND | 资源不存在 |
| 413 | FILE_TOO_LARGE / QUOTA_EXCEEDED | 文件过大或租户配额超限 |
| 500 | INTERNAL_SERVER_ERROR | 服务器内部错误 |

---

## 0. V2 Upload Session API（开发中）

`/api/v1/upload-sessions*` 是当前 `file-core` 驱动的统一上传会话 facade，用于把 legacy 直传初始化 / progress / complete / 分片 URL 签发 / 中止语义迁入新架构。

### 0.1 创建上传会话

**端点**: `POST /api/v1/upload-sessions`

**请求体**:

```json
{
  "uploadMode": "AUTO",
  "accessLevel": "PRIVATE",
  "originalFilename": "demo.mp4",
  "contentType": "video/mp4",
  "expectedSize": 11534336,
  "fileHash": "hash-001"
}
```

**当前行为**:

- `AUTO` 是当前前台默认推荐模式；服务端会按文件大小自动路由到 `PRESIGNED_SINGLE` 或 `DIRECT`
- `DIRECT` 会立即初始化 multipart upload，适合大文件和需要断点续传的场景
- `PRESIGNED_SINGLE` 创建单对象上传会话，响应中直接返回 `singleUploadUrl`、`singleUploadMethod`、`singleUploadExpiresInSeconds`、`singleUploadHeaders`
- 如果 `fileHash` 命中同租户、同用户、同模式、同访问级别、同文件大小的未过期会话，则直接返回已有会话用于续传
- 如果 `fileHash` 已命中目标 bucket 中的现有对象，则直接创建 `COMPLETED` 状态会话，并在响应中返回 `fileId`
- `DIRECT` 响应中会返回 `chunkSizeBytes`、`totalParts`、`status`
- `PRESIGNED_SINGLE` 响应中的 `chunkSizeBytes=0`、`totalParts=1`
- `INLINE` 会话可创建，但不会生成 multipart 上下文；它只适合低并发且需要同步服务端处理的场景，不建议作为前台默认上传模式

**响应体示例（PRESIGNED_SINGLE）**:

```json
{
  "uploadSession": {
    "uploadSessionId": "session-ps-001",
    "uploadMode": "PRESIGNED_SINGLE",
    "accessLevel": "PUBLIC",
    "originalFilename": "avatar.png",
    "contentType": "image/png",
    "expectedSize": 512,
    "fileHash": "hash-ps-001",
    "chunkSizeBytes": 0,
    "totalParts": 1,
    "fileId": null,
    "status": "INITIATED"
  },
  "resumed": false,
  "instantUpload": false,
  "completedPartNumbers": [],
  "completedPartInfos": [],
  "singleUploadUrl": "https://storage.example.com/...",
  "singleUploadMethod": "PUT",
  "singleUploadExpiresInSeconds": 900,
  "singleUploadHeaders": {
    "Content-Type": "image/png"
  }
}
```

### 0.2 查询上传会话

**端点**: `GET /api/v1/upload-sessions/{uploadSessionId}`

用于恢复当前会话状态、分片规划和过期时间。

### 0.3 批量签发分片上传 URL

**端点**: `POST /api/v1/upload-sessions/{uploadSessionId}/part-urls`

**请求体**:

```json
{
  "partNumbers": [1, 2, 3]
}
```

**响应体**:

```json
{
  "uploadSessionId": "session-001",
  "partUrls": [
    {
      "partNumber": 1,
      "uploadUrl": "https://storage.example.com/...",
      "expiresInSeconds": 900
    }
  ]
}
```

**约束**:

- `partNumbers` 不能为空、不能重复、必须落在 `1..totalParts` 范围内
- `INLINE` 模式请求该接口返回 `400`，因为代理上传链路不签发分片直传 URL

### 0.4 查询上传进度

**端点**: `GET /api/v1/upload-sessions/{uploadSessionId}/progress`

**当前行为**:

- 直接从对象存储读取 authoritative uploaded parts，而不是信任客户端本地状态
- 返回 `completedPartNumbers` 与包含 `etag/sizeBytes` 的 `completedPartInfos`

### 0.5 完成上传会话

**端点**: `POST /api/v1/upload-sessions/{uploadSessionId}/complete`

**请求体**:

```json
{
  "contentType": "video/mp4",
  "parts": [
    {
      "partNumber": 1,
      "etag": "etag-1"
    }
  ]
}
```

**当前行为**:

- 服务端会先从对象存储读取 authoritative parts，并与请求中的 `parts` 做交叉校验
- 所有分片齐全后才会执行 multipart complete
- complete 成功后会直接写入 `storage_objects` 与 `file_records`

### 0.6 中止上传会话

**端点**: `POST /api/v1/upload-sessions/{uploadSessionId}/abort`

成功返回 `204 No Content`。如果会话已完成，则返回 `400`。`DIRECT` 会话会中止 multipart upload；`PRESIGNED_SINGLE` 会话会删除已上传对象。

### 0.7 相关配置

`file.core.upload` 当前支持以下配置：

```yaml
file:
  core:
    upload:
      session-ttl: 24h
      part-url-ttl: 15m
      completion-wait-timeout: 5s
      completion-poll-interval: 100ms
      chunk-size-bytes: 5242880
      max-parts: 10000
```

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

### 2.1 签发文件访问票据

为文件签发短期访问票据，客户端随后应访问 `gatewayUrl`。

**端点**: `POST /api/v1/files/{fileId}:issue-access-ticket`

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
curl -X POST http://localhost:8089/api/v1/files/01JGXXX-XXX-XXX:issue-access-ticket \
  -H "X-App-Id: blog" \
  -H "X-User-Id: 12345" \
  -H "Authorization: Bearer eyJhbGc..."
```

**响应示例**:

```json
{
  "ticket": "encoded.ticket",
  "gatewayUrl": "http://localhost:8090/api/v1/files/01JGXXX-XXX-XXX/content?ticket=encoded.ticket",
  "expiresAt": "2026-01-19T12:00:00Z"
}
```

---

### 2.2 批量签发文件访问票据

为多个文件一次性签发访问票据，适合页面或列表批量图片场景。每个 `fileId` 仍返回独立的 `ticket` 与 `gatewayUrl`。

**端点**: `POST /api/v1/files:batch-issue-access-ticket`

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**请求示例**:

```bash
curl -X POST http://localhost:8089/api/v1/files:batch-issue-access-ticket \
  -H "X-App-Id: blog" \
  -H "X-User-Id: 12345" \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "fileIds": ["01JGFILE-001", "01JGFILE-002", "01JGFILE-003"]
  }'
```

**响应示例**:

```json
{
  "items": [
    {
      "fileId": "01JGFILE-001",
      "ticket": "encoded.ticket.1",
      "gatewayUrl": "http://localhost:8090/api/v1/files/01JGFILE-001/content?ticket=encoded.ticket.1",
      "expiresAt": "2026-01-19T12:00:00Z",
      "errorCode": null,
      "message": null
    },
    {
      "fileId": "01JGFILE-002",
      "ticket": null,
      "gatewayUrl": null,
      "expiresAt": null,
      "errorCode": "ACCESS_DENIED",
      "message": "access denied for fileId: 01JGFILE-002"
    }
  ]
}
```

---

### 2.3 获取文件详情

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
  "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
  "tenantId": "blog",
  "ownerId": "12345",
  "originalFilename": "photo.jpg",
  "fileSize": 102400,
  "contentType": "image/webp",
  "accessLevel": "PUBLIC",
  "status": "ACTIVE"
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

本节描述的是“服务端中转的分片上传”：

- 前端把分片字节上传到 `file-service`
- `file-service` 再调用 MinIO/S3 Multipart API 上传分片
- 当前 v1 `multipart` 控制层已收口为 legacy 协议适配，底层通过 `MultipartUploadCoreBridgeService` 复用 `file-core` 的 `DIRECT` upload session

如果需要“预签名直传分片”，请看后面的“5. 预签名直传 API”。

### 4.1 初始化分片上传

初始化一个分片上传任务；如果传入 `fileHash` 且命中同用户未过期会话，会直接返回已有任务用于断点续传。

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
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
  "contentType": "video/mp4"
}
```

**请求参数说明**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| fileName | String | 是 | 文件名 |
| fileSize | Long | 是 | 文件总大小（字节） |
| fileHash | String | 否 | 文件哈希，用于断点续传匹配 |
| contentType | String | 是 | MIME 类型 |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "uploadId": "s3-upload-id-xxx",
    "chunkSize": 5242880,
    "totalParts": 20,
    "completedParts": [1, 2, 3]
  }
}
```

---

### 4.2 上传分片

上传单个分片。请求体是原始二进制字节流，不是 `multipart/form-data`。

**端点**: `PUT /api/v1/multipart/{taskId}/parts/{partNumber}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |
| partNumber | Integer | 是 | 分片编号（从 1 开始） |

**请求头**:
```http
Content-Type: application/octet-stream
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

原始分片字节流。

**请求示例**:

```bash
curl -X PUT "http://localhost:8089/api/v1/multipart/01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ/parts/1" \
  -H "X-App-Id: blog" \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@chunk-1.bin"
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "\"d41d8cd98f00b204e9800998ecf8427e\""
}
```

---

### 4.3 完成分片上传

完成分片上传，服务端会读取对象存储中的 authoritative parts 调用 `CompleteMultipartUpload`，并生成 `fileId`。

**端点**: `POST /api/v1/multipart/{taskId}/complete`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |

**请求头**:
```http
X-App-Id: blog
Authorization: Bearer {token}
```

**请求体**:

无。

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
    "url": "https://cdn.example.com/blog/2026/01/19/12345/videos/xxx.mp4"
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

### 4.5 查询上传进度

查询当前上传任务的进度。当前进度视图直接来自底层 upload session 和对象存储分片状态，而不是仅依赖 legacy 任务表缓存。

**端点**: `GET /api/v1/multipart/{taskId}/progress`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| taskId | String | 是 | 上传任务 ID |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "totalParts": 20,
    "completedParts": 3,
    "uploadedBytes": 15728640,
    "totalBytes": 104857600,
    "percentage": 15
  }
}
```

---

## 5. 预签名直传 API

本项目有两类预签名直传：

- 分片预签名直传：`/api/v1/direct-upload/*`
- 单文件预签名直传：`/api/v1/upload-sessions` + `uploadMode=PRESIGNED_SINGLE`

### 5.1 分片预签名直传初始化

初始化分片直传任务。前端拿到 `taskId` 和 `uploadId` 后，再按需申请每个分片的上传 URL。

说明：

- `fileHash` 为必填，用于秒传和断点续传匹配
- 如果命中未完成任务，接口会返回 `completedParts` 和 `completedPartInfos`
- `completedPartInfos` 中的 `etag` 可直接用于后续完成上传或前端状态恢复

**端点**: `POST /api/v1/direct-upload/init`

**请求体**:

```json
{
  "fileName": "large-video.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "uploadId": "s3-upload-id-xxx",
    "storagePath": "blog/2026/03/13/12345/files/01JGXXX.bin",
    "chunkSize": 5242880,
    "totalParts": 20,
    "completedParts": [1, 2],
    "completedPartInfos": [
      {
        "partNumber": 1,
        "etag": "\"etag-1\""
      },
      {
        "partNumber": 2,
        "etag": "\"etag-2\""
      }
    ],
    "isResume": true,
    "isInstantUpload": false,
    "fileId": null,
    "fileUrl": null
  }
}
```

---

### 5.2 获取分片上传 URL

为一个或多个分片生成预签名上传 URL。前端拿到 URL 后，直接把分片上传到 MinIO/S3，不经过 `file-service` 中转。

**端点**: `POST /api/v1/direct-upload/part-urls`

**请求体**:

```json
{
  "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
  "partNumbers": [1, 2, 3]
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "partUrls": [
      {
        "partNumber": 1,
        "uploadUrl": "https://minio.example.com/bucket/object?partNumber=1&uploadId=xxx",
        "expiresIn": 900
      },
      {
        "partNumber": 2,
        "uploadUrl": "https://minio.example.com/bucket/object?partNumber=2&uploadId=xxx",
        "expiresIn": 900
      }
    ]
  }
}
```

---

### 5.3 查询分片预签名直传进度

查询当前任务在 MinIO/S3 中已经完成的分片，用于页面刷新、断线重连后的状态恢复。

**端点**: `GET /api/v1/direct-upload/{taskId}/progress`

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
    "totalParts": 20,
    "completedParts": 2,
    "uploadedBytes": 10485760,
    "totalBytes": 104857600,
    "percentage": 10,
    "completedPartNumbers": [1, 2],
    "completedPartInfos": [
      {
        "partNumber": 1,
        "etag": "\"etag-1\""
      },
      {
        "partNumber": 2,
        "etag": "\"etag-2\""
      }
    ]
  }
}
```

---

### 5.4 完成分片预签名直传

前端把所有分片直接上传到 MinIO/S3 后，调用完成接口。

说明：

- 如果前端保留了 `partNumber + etag` 列表，可以一并传回，服务端会校验这些信息
- 如果前端在刷新页面后丢失了本地 `etag` 缓存，也可以不传 `parts`，服务端会以对象存储中的已上传分片为准完成合并
- 服务端会拒绝与 MinIO/S3 当前状态不一致的 `etag`

**端点**: `POST /api/v1/direct-upload/complete`

**请求体**:

```json
{
  "taskId": "01JGZZZ-ZZZ-ZZZ-ZZZ-ZZZZZZZZZZZZ",
  "contentType": "video/mp4",
  "parts": [
    {
      "partNumber": 1,
      "etag": "\"etag-1\""
    },
    {
      "partNumber": 2,
      "etag": "\"etag-2\""
    }
  ]
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX"
}
```

---

### 5.5 创建单文件预签名上传会话

适用于不需要分片的单文件直传。

**端点**: `POST /api/v1/upload-sessions`

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
X-User-Id: user-001
```

**请求体**:

```json
{
  "uploadMode": "PRESIGNED_SINGLE",
  "accessLevel": "PUBLIC",
  "originalFilename": "photo.jpg",
  "contentType": "image/jpeg",
  "expectedSize": 102400,
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**响应示例**:

```json
{
  "uploadSession": {
    "uploadSessionId": "session-001",
    "tenantId": "blog",
    "ownerId": "user-001",
    "uploadMode": "PRESIGNED_SINGLE",
    "accessLevel": "PUBLIC",
    "originalFilename": "photo.jpg",
    "contentType": "image/jpeg",
    "expectedSize": 102400,
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "status": "INITIATED"
  },
  "resumed": false,
  "instantUpload": false,
  "completedPartNumbers": [],
  "completedPartInfos": [],
  "singleUploadUrl": "https://s3.amazonaws.com/bucket/path?X-Amz-Signature=...",
  "singleUploadMethod": "PUT",
  "singleUploadExpiresInSeconds": 900,
  "singleUploadHeaders": {
    "Content-Type": "image/jpeg"
  }
}
```

---

### 5.6 完成单文件预签名上传

客户端使用 `singleUploadUrl` 上传完成后，调用此接口完成上传会话并创建文件记录。

**端点**: `POST /api/v1/upload-sessions/{uploadSessionId}/complete`

**请求头**:
```http
Content-Type: application/json
X-App-Id: blog
X-User-Id: user-001
```

**请求体**:

```json
{
  "contentType": "image/jpeg"
}
```

**响应示例**:

```json
{
  "uploadSessionId": "session-001",
  "fileId": "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX",
  "status": "COMPLETED"
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
| 小文件 (<5MB) | 直接上传或单文件预签名直传 | 简单快速 |
| 大文件 (>10MB) | `/api/v1/multipart/*` | 服务端中转，支持断点续传 |
| 大文件客户端直传 | `/api/v1/direct-upload/*` | 分片预签名直传，减轻服务器压力 |
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
| 400 | UPLOAD_TASK_NOT_FOUND | 上传任务不存在 |
| 400 | UPLOAD_TASK_EXPIRED | 上传任务已过期 |
| 400 | TASK_STATUS_INVALID | 上传任务状态不允许当前操作 |
| 400 | FILE_SIZE_MISMATCH | 断点续传文件大小不匹配 |
| 400 | PART_COUNT_EXCEEDED | 分片数量超出系统限制 |
| 400 | PART_NUMBER_INVALID | 分片号非法 |
| 400 | PART_NUMBER_DUPLICATED | 请求中分片号重复 |
| 400 | PARTS_INCOMPLETE | 仍有分片未上传完成 |
| 400 | PART_NOT_FOUND_IN_STORAGE | 对象存储中不存在对应分片 |
| 400 | PART_ETAG_MISMATCH | 客户端分片 ETag 与对象存储不一致 |
| 400 | UNSUPPORTED_ACCESS_LEVEL | 不支持的访问级别 |
| 400 | FILENAME_EMPTY / EXTENSION_REQUIRED | 文件名或扩展名不合法 |
| 400 | EXTENSION_NOT_ALLOWED / CONTENT_TYPE_NOT_ALLOWED | 文件类型不在允许列表中 |
| 400 | FILE_TYPE_CONTENT_MISMATCH | 文件声明类型与魔数检测结果不一致 |
| 400 | FILE_HASH_FAILED | 文件哈希计算失败 |
| 500 | FILE_UPLOAD_FAILED / IMAGE_UPLOAD_FAILED | 文件或图片上传处理失败 |
| 500 | IMAGE_PROCESS_FAILED / THUMBNAIL_GENERATE_FAILED | 图片处理或缩略图生成失败 |
| 500 | S3_CLIENT_ERROR | S3/MinIO 客户端调用失败 |

**错误响应示例（租户停用）**:
```json
{
  "code": 403,
  "errorCode": "TENANT_SUSPENDED",
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
  "errorCode": "QUOTA_EXCEEDED",
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
