# 前端直传 MinIO 接入说明

## 目标

前端不再通过 `file-service` 中转文件内容，而是：

1. 向 `file-service` 申请直传 URL
2. 直接把文件上传到 MinIO
3. 上传完成后通知 `file-service` 完成上传会话并落库
4. 业务系统只保存 `fileId`

这样可以把大文件流量从 `file-service` 剥离出去，只保留鉴权、配额、元数据和访问控制。

## 标准流程

### 1. 创建单文件上传会话

请求：

- `POST /api/v1/upload-sessions`
- Header:
  - `X-App-Id: {appId}`
  - `X-User-Id: {userId}`
  - 需要已登录用户身份

请求体示例：

```json
{
  "uploadMode": "PRESIGNED_SINGLE",
  "accessLevel": "PUBLIC",
  "originalFilename": "avatar.png",
  "contentType": "image/png",
  "expectedSize": 102400,
  "fileHash": "md5-hex"
}
```

返回关键信息：

- `uploadSession.uploadSessionId`: 上传会话 ID
- `singleUploadUrl`: 前端真正上传到 MinIO 的地址
- `singleUploadMethod`: 当前为 `PUT`
- `singleUploadHeaders`: 当前至少包含 `Content-Type`
- `singleUploadExpiresInSeconds`: 直传 URL 过期时间（秒）

### 2. 前端直接上传到 MinIO

前端使用上一步返回的 `singleUploadUrl` 直接发起 `PUT` 请求。

要求：

- 请求方法必须使用返回值中的 `singleUploadMethod`
- `Content-Type` 必须与申请预签名时保持一致
- 文件内容直接上传到 MinIO，不经过 `file-service`

示例：

```http
PUT {singleUploadUrl}
Content-Type: image/png

<binary>
```

### 3. 上传成功后完成上传会话

请求：

- `POST /api/v1/upload-sessions/{uploadSessionId}/complete`
- Header:
  - `X-App-Id: {appId}`
  - `X-User-Id: {userId}`
  - 需要已登录用户身份

请求体示例：

```json
{
  "contentType": "image/png"
}
```

返回关键信息：

- `fileId`: 业务系统应保存的文件标识
- `status`: 当前会话状态，成功时为 `COMPLETED`

## 业务系统应该保存什么

应该保存：

- `fileId`

不应该保存：

- `presignedUrl`
- `storagePath`
- MinIO 真实访问 URL

原因：

- `presignedUrl` 会过期
- `storagePath` 属于底层存储细节
- 真实访问 URL 未来可能切换为 CDN、网关或其他存储实现

## 文件访问方式

上传完成后，其他服务或前端应优先使用 `fileId` 再向 `file-service` 申请访问票据。

### 签发访问票据

- `POST /api/v1/files/{fileId}:issue-access-ticket`

返回字段：

- `ticket`: 当前下载票据
- `gatewayUrl`: `file-gateway-service` 访问地址，格式为 `/api/v1/files/{fileId}/content?ticket=...`
- `expiresAt`: 票据过期时间

建议：

- 前端优先使用 `gatewayUrl`
- 业务后端如果只需要文件元信息，优先调用 `GET /api/v1/files/{fileId}`

### 网关访问

- `GET /api/v1/files/{fileId}/content`

该接口由 `file-gateway-service` 提供，负责校验票据后再重定向到真实文件地址。

## 当前实现约束

### `complete` 之前文件不算完成

文件上传到 MinIO 后，如果没有调用 `complete`：

- 不会生成 `fileId`
- 业务系统无法稳定引用该文件
- 该对象只存在于底层存储，不属于完整业务记录

### `complete` 以对象存储真实元数据为准

`file-service` 在完成时会通过 `HeadObject` 获取：

- 实际文件大小
- 实际 `Content-Type`

最终落库使用的是存储层真实元数据，不完全依赖前端提交值。

### 公私有访问级别会影响返回 URL

- `public`: 一般返回公开访问地址
- `private`: 一般返回临时签名地址

因此业务系统只应依赖 `fileId`，不要假设返回地址长期不变。

## 前端失败处理建议

### 创建会话成功，但上传失败

处理方式：

- 重新申请新的预签名 URL
- 不要复用已过期或已失败的 `singleUploadUrl`

### 上传到 MinIO 成功，但 `complete` 失败

处理方式：

- 可以使用同一个 `uploadSessionId` 重试 `complete`
- 如果长时间未确认成功，应由后台清理孤儿对象

### 文件重复上传

当前服务会基于 `fileHash` 做去重；
是否命中已有对象，以 `confirm` 阶段的存储对象记录为准。

## 推荐的前端伪代码

```ts
async function uploadFile(file: File) {
  const fileHash = await calcMd5(file)

  const session = await api.post("/api/v1/upload-sessions", {
    uploadMode: "PRESIGNED_SINGLE",
    accessLevel: "PUBLIC",
    originalFilename: file.name,
    contentType: file.type,
    expectedSize: file.size,
    fileHash
  })

  await fetch(session.data.singleUploadUrl, {
    method: session.data.singleUploadMethod,
    headers: session.data.singleUploadHeaders,
    body: file
  })

  const completion = await api.post(
    `/api/v1/upload-sessions/${session.data.uploadSession.uploadSessionId}/complete`,
    { contentType: file.type }
  )

  const detail = await api.get(`/api/v1/files/${completion.data.fileId}`)

  return detail.data.fileId
}
```

## 结论

推荐把单文件上传链路统一为：

- 前端通过 `upload-sessions` 创建 `PRESIGNED_SINGLE` 会话
- 前端直传 MinIO
- `file-service` 只负责会话创建、完成和 `fileId -> URL`
- 业务系统只保存 `fileId`

这条链路已经不再依赖旧 `presign/confirm` 兼容接口。
