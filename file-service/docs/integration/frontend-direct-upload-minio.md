# 前端直传 MinIO 接入说明

## 目标

前端不再通过 `file-service` 中转文件内容，而是：

1. 向 `file-service` 申请直传 URL
2. 直接把文件上传到 MinIO
3. 上传完成后通知 `file-service` 确认落库
4. 业务系统只保存 `fileId`

这样可以把大文件流量从 `file-service` 剥离出去，只保留鉴权、配额、元数据和访问控制。

## 标准流程

### 1. 获取预签名直传 URL

请求：

- `POST /api/v1/upload/presign`
- Header:
  - `X-App-Id: {appId}`
  - 需要已登录用户身份

请求体示例：

```json
{
  "fileName": "avatar.png",
  "fileSize": 102400,
  "contentType": "image/png",
  "fileHash": "md5-hex",
  "accessLevel": "public"
}
```

返回关键信息：

- `presignedUrl`: 前端真正上传到 MinIO 的地址
- `storagePath`: 该文件在对象存储中的路径
- `method`: 当前为 `PUT`
- `headers`: 当前至少包含 `Content-Type`
- `expiresAt`: 直传 URL 过期时间

### 2. 前端直接上传到 MinIO

前端使用上一步返回的 `presignedUrl` 直接发起 `PUT` 请求。

要求：

- 请求方法必须使用返回值中的 `method`
- `Content-Type` 必须与申请预签名时保持一致
- 文件内容直接上传到 MinIO，不经过 `file-service`

示例：

```http
PUT {presignedUrl}
Content-Type: image/png

<binary>
```

### 3. 上传成功后调用确认接口

请求：

- `POST /api/v1/upload/confirm`
- Header:
  - `X-App-Id: {appId}`
  - 需要已登录用户身份

请求体示例：

```json
{
  "appId": "blog",
  "storagePath": "blog/2026/03/12/10001/files/019c....png",
  "fileHash": "md5-hex",
  "originalFilename": "avatar.png",
  "accessLevel": "public"
}
```

返回关键信息：

- `fileId`: 业务系统应保存的文件标识
- `url`: 当前解析出的真实文件访问地址

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

### `confirm` 之前文件不算完成

文件上传到 MinIO 后，如果没有调用 `confirm`：

- 不会生成 `fileId`
- 业务系统无法稳定引用该文件
- 该对象只存在于底层存储，不属于完整业务记录

### `confirm` 以对象存储真实元数据为准

`file-service` 在确认时会通过 `HeadObject` 获取：

- 实际文件大小
- 实际 `Content-Type`

最终落库使用的是存储层真实元数据，不完全依赖前端提交值。

### 公私有访问级别会影响返回 URL

- `public`: 一般返回公开访问地址
- `private`: 一般返回临时签名地址

因此业务系统只应依赖 `fileId`，不要假设返回地址长期不变。

## 前端失败处理建议

### 申请预签名成功，但上传失败

处理方式：

- 重新申请新的预签名 URL
- 不要复用已过期或已失败的 `presignedUrl`

### 上传到 MinIO 成功，但 `confirm` 失败

处理方式：

- 可以用相同 `storagePath` 重试 `confirm`
- 如果长时间未确认成功，应由后台清理孤儿对象

### 文件重复上传

当前服务会基于 `fileHash` 做去重；
是否命中已有对象，以 `confirm` 阶段的存储对象记录为准。

## 推荐的前端伪代码

```ts
async function uploadFile(file: File) {
  const fileHash = await calcMd5(file)

  const presign = await api.post("/api/v1/upload/presign", {
    fileName: file.name,
    fileSize: file.size,
    contentType: file.type,
    fileHash,
    accessLevel: "public"
  })

  await fetch(presign.data.presignedUrl, {
    method: presign.data.method,
    headers: presign.data.headers,
    body: file
  })

  const confirm = await api.post("/api/v1/upload/confirm", {
    appId: currentAppId,
    storagePath: presign.data.storagePath,
    fileHash,
    originalFilename: file.name,
    accessLevel: "public"
  })

  return confirm.data.fileId
}
```

## 结论

推荐把上传链路统一为：

- 前端直传 MinIO
- `file-service` 只负责 `presign`、`confirm`、`fileId -> URL`
- 业务系统只保存 `fileId`

这也是当前项目最适合扩展并发的上传方案。
