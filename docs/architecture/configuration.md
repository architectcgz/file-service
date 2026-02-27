# 配置与中间件

## 配置属性类

### S3Properties (`storage.s3`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| endpoint | S3 端点地址 | — |
| publicEndpoint | 公开访问端点 | — |
| accessKey | 访问密钥 | — |
| secretKey | 密钥 | — |
| bucket | 默认 Bucket | — |
| publicBucket | 公开文件 Bucket | — |
| privateBucket | 私有文件 Bucket | — |
| region | 区域 | us-east-1 |
| cdnDomain | CDN 域名（可选） | — |
| pathStyleAccess | Path-style 访问 | true |

### MultipartProperties (`storage.multipart`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| enabled | 是否启用分片上传 | true |
| threshold | 触发分片的文件大小阈值 | 10MB |
| chunkSize | 分片大小 | 5MB |
| maxParts | 最大分片数 | 10000 |
| taskExpireHours | 任务过期时间 | 24h |
| cleanupCron | 清理定时任务 Cron | `0 0 * * * *` |

### FileTypeProperties (`file-service.file-type`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| images.allowedTypes | 允许的图片 MIME 类型 | image/jpeg, image/png, image/gif, image/webp |
| images.maxSize | 图片最大大小 | — |
| videos.allowedTypes | 允许的视频 MIME 类型 | video/mp4, video/quicktime 等 |
| videos.maxSize | 视频最大大小 | — |
| documents.allowedTypes | 允许的文档 MIME 类型 | application/pdf, docx, xlsx 等 |
| documents.maxSize | 文档最大大小 | — |

### ImageProcessingProperties (`file-service.image`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| compress.enabled | 是否启用压缩 | true |
| compress.maxWidth | 最大宽度 | — |
| compress.maxHeight | 最大高度 | — |
| compress.quality | 压缩质量 | — |
| webp.enabled | 是否启用 WebP 转换 | true |
| thumbnail.enabled | 是否生成缩略图 | true |

### AccessProperties (`file-service.access`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| privateUrlExpiration | 私有文件预签名 URL 有效期 | 3600s |
| presignedUrlExpiration | 预签名上传 URL 有效期 | 900s |

### CacheProperties (`file-service.cache`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| enabled | 是否启用缓存 | true |
| url.ttl | 公开文件 URL 缓存 TTL | 3600s |

### UploadProperties (`file-service.upload`)

| 属性 | 说明 | 默认值 |
|------|------|--------|
| maxFileSize | 最大文件大小 | 5GB |

### AdminProperties

| 属性 | 说明 | 默认值 |
|------|------|--------|
| enabled | 是否启用管理员 API | — |

## 中间件依赖

| 中间件 | 用途 | 备注 |
|--------|------|------|
| PostgreSQL | 持久化存储 | 文件记录、上传任务、租户等 |
| Redis (Redisson) | 缓存 | 公开文件 URL 缓存 |
| S3 兼容存储 | 对象存储 | 支持 MinIO / RustFS / AWS S3 / 阿里云 OSS |
| Nacos | 注册中心 + 配置中心 | 通过 bootstrap.yml 接入 |
| Micrometer + Prometheus | 监控指标 | 上传量、存储使用等 |

## 缓存设计

### 缓存 Key

| Key 模式 | 说明 | TTL |
|----------|------|-----|
| `file:url:{fileId}` | 公开文件访问 URL | 3600s |

### 缓存策略

- 仅缓存公开文件（PUBLIC）的访问 URL
- 私有文件预签名 URL 有时效性，不缓存
- 文件访问级别变更时清除对应缓存
- 文件删除时清除对应缓存
- 缓存失败不影响业务（降级处理）

## 存储路径设计

格式：`{appId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}`

示例：`blog/2026/02/27/user123/files/550e8400-e29b-41d4-a716-446655440000.jpg`

类型目录：
- `images` — 图片文件
- `files` — 普通文件
- `thumbnails` — 缩略图

## 双 Bucket 架构

```
publicBucket  — 存放 PUBLIC 文件，支持 CDN 加速，永久 URL
privateBucket — 存放 PRIVATE 文件，仅通过预签名 URL 访问
```

文件访问级别变更时，会在两个 Bucket 之间迁移对象。

## 服务端口与基础配置

| 配置项 | 值 |
|--------|-----|
| 服务端口 | 8089 |
| Spring multipart 上传限制 | 200MB |
| 数据库 | PostgreSQL (localhost:5432/file_service) |
| Redis | localhost:6379 |
