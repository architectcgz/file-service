# File Gateway Service

独立文件网关服务，负责接收前端文件访问请求，向 `file-service` 发起鉴权访问，再返回 302 重定向到最终 CDN 或对象存储地址。

## 能力

- 暴露统一访问入口：`GET /api/v1/files/{fileId}/content`
- 支持两种身份传递方式
- 受信任服务调用：`X-App-Id`、`X-User-Id`
- 前端原生访问：`appId`、`userId`、`expiresAt`、`signature` 查询参数
- 原样透传 `file-service` 返回的 `Location` 与 `Cache-Control`

## 配置

```yaml
gateway:
  upstream:
    base-url: http://localhost:8089
  auth:
    allow-header-identity: true
    signing-secret: change-me-before-production
```

环境变量：

- `FILE_SERVICE_BASE_URL`
- `FILE_GATEWAY_ALLOW_HEADER_IDENTITY`
- `FILE_GATEWAY_SIGNING_SECRET`
- `FILE_GATEWAY_CONNECT_TIMEOUT`
- `FILE_GATEWAY_READ_TIMEOUT`

## 签名参数

签名载荷格式：

```text
GET
/api/v1/files/{fileId}/content
{appId}
{userId-or-empty}
{expiresAt}
```

签名算法：`HMAC-SHA256`，输出使用 Base64 URL Safe，无 padding。

示例请求：

```text
/api/v1/files/file-001/content?appId=blog&userId=user-001&expiresAt=1760000000&signature=xxxx
```

`userId` 可为空。适用于公开文件匿名访问，但仍然会保留 `appId` 租户隔离。
