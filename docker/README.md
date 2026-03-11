# File Service Docker 环境

本文档说明如何使用 Docker 部署和运行基于 `MinIO` 的 File Service 独立环境。

## 概述

当前推荐部署形态包含：

- `PostgreSQL`：独立数据库 `file_service`
- `Redis`：URL 缓存和上传态缓存
- `MinIO`：S3 兼容对象存储
- `File Service`：文件元数据、鉴权和 URL 签发
- `File Gateway Service`：前端统一访问入口

## 目录结构

```text
file-service/docker/
├── docker-compose.yml
├── docker-compose.prod.yml
├── minio-init/
├── migrations/
├── monitoring/
└── README.md
```

## 快速开始

### 1. 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 2GB 可用内存

### 2. 启动共享基础设施

开发编排依赖共享网络 `shared-infra`。先启动公共基础设施仓库中的 PostgreSQL 和 Redis，再启动当前项目：

```bash
cd infra
docker compose up -d
```

### 3. 启动 File Service + Gateway + MinIO

```bash
cd /home/azhi/workspace/projects/file-service/docker
docker compose --profile minio up -d
```

生产编排使用：

```bash
docker compose -f docker-compose.prod.yml --profile minio up -d
```

### 4. 检查服务状态

```bash
docker compose --profile minio ps
```

预期包含：

- `file-service-minio`
- `file-service-minio-init`
- `file-service-app`
- `file-gateway-app`
- `file-service-db-migrate`

### 5. 健康检查

```bash
curl http://localhost:8089/actuator/health
curl http://localhost:8090/actuator/health
```

## 端口说明

- `8089`：File Service
- `8090`：File Gateway Service
- `9000`：MinIO API
- `9001`：MinIO Console

## 关键环境变量

### File Service

- `FILE_SERVICE_PORT=8089`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `S3_ENDPOINT=http://file-service-minio:9000`
- `S3_PUBLIC_ENDPOINT=http://localhost:9000`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `S3_PUBLIC_BUCKET=platform-files-public`
- `S3_PRIVATE_BUCKET=platform-files-private`

### File Gateway Service

- `FILE_GATEWAY_PORT=8090`
- `FILE_SERVICE_BASE_URL=http://file-service-minio-app:8089`
- `FILE_GATEWAY_ALLOW_HEADER_IDENTITY=true`
- `FILE_GATEWAY_SIGNING_SECRET=change-me-before-production`

## 访问链路

推荐链路如下：

1. 业务服务只保存 `file_id`
2. 前端访问 `File Gateway Service`
3. 网关调用 `File Service /api/v1/files/{fileId}/content`
4. `File Service` 完成租户隔离和访问控制
5. 最终 302 到 CDN / MinIO 对象地址

## 前端接入

### 受信任服务转发

适用于 BFF、服务端渲染、后端代理：

```http
GET /api/v1/files/{fileId}/content
X-App-Id: blog
X-User-Id: user-001
```

### 前端原生访问

适用于 `<img src>`、`<video>`、浏览器直链访问：

```text
/api/v1/files/{fileId}/content?appId=blog&userId=user-001&expiresAt=1760000000&signature=xxxx
```

签名算法、载荷格式和示例见：
[file-gateway-service/README.md](/home/azhi/workspace/projects/file-service/file-gateway-service/README.md)

## 数据迁移

项目不使用 Flyway。数据库初始化通过独立迁移容器完成：

- 脚本位置：`docker/migrations/migrate.sh`
- SQL 目录：`docker/migrations/sql/`
- 版本表：`schema_migrations`

## MinIO 存储桶

默认会初始化两个桶：

- `platform-files-public`
- `platform-files-private`

其中：

- 公开文件存入 public bucket，可直接访问
- 私有文件存入 private bucket，通过预签名 URL 访问

## 常用命令

启动：

```bash
docker compose --profile minio up -d
```

停止：

```bash
docker compose --profile minio down
```

查看日志：

```bash
docker compose logs -f file-service-minio-app
docker compose logs -f file-gateway-minio-app
docker compose logs -f file-service-minio
```

查看 MinIO 控制台：

```text
http://localhost:9001
```

## 故障排查

### MinIO 无法访问

检查：

```bash
docker compose ps file-service-minio
docker compose logs file-service-minio
```

### File Service 未启动

检查：

```bash
docker compose logs file-service-db-migrate
docker compose logs file-service-minio-app
```

### Gateway 访问 401

通常是以下原因：

- 缺少 `X-App-Id`
- 浏览器签名参数缺失
- `expiresAt` 过期
- `signature` 与服务端密钥不一致

### Gateway 访问 403

通常表示：

- 私有文件但没有正确 `userId`
- 用户不是文件所有者

### Gateway 访问 404

通常表示：

- `fileId` 不存在
- `appId` 不匹配，触发跨租户隔离

## 说明

仓库中仍保留历史兼容配置，但当前交付和文档统一以 `MinIO` 为准。
