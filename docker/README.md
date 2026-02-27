# File Service Docker 环境

本文档说明如何使用 Docker 部署和运行 File Service 独立环境。

## 概述

File Service 拥有独立的 Docker 环境，包含：

- **PostgreSQL**: 独立数据库 `file_service`
- **RustFS/MinIO**: S3 兼容对象存储
- **File Service**: 文件服务应用

## 目录结构

```
file-service/docker/
├── docker-compose.yml          # Docker Compose 配置
├── .env.example                # 环境变量示例
├── .env                        # 环境变量配置（需创建）
├── postgres-init/              # 数据库初始化脚本
│   └── init-file-service-db.sql
├── rustfs/                     # RustFS 配置（如果使用）
│   └── config.toml
├── scripts/                    # 辅助脚本
│   ├── start.sh               # 启动脚本
│   ├── stop.sh                # 停止脚本
│   └── verify-checkpoint.ps1  # 验证脚本
├── README.md                   # 本文档
├── CHECKPOINT_STATUS.md        # 检查点状态
└── CHECKPOINT_VERIFICATION_GUIDE.md  # 验证指南
```

## 快速开始

### 1. 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 2GB 可用内存
- 至少 10GB 可用磁盘空间

### 2. 配置环境变量

复制环境变量示例文件：

```bash
cd file-service/docker
cp .env.example .env
```

编辑 `.env` 文件，根据需要修改配置：

```env
# PostgreSQL 配置
POSTGRES_VERSION=14
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=file_service
POSTGRES_PORT=5432

# PostgreSQL 时区配置
# TZ: 设置容器系统时区
# PGTZ: 设置 PostgreSQL 数据库时区
# 两者都设置为 Asia/Shanghai (UTC+8)
TZ=Asia/Shanghai
PGTZ=Asia/Shanghai

# RustFS/MinIO 配置
RUSTFS_VERSION=latest
RUSTFS_PORT=9001
RUSTFS_CONSOLE_PORT=9002
RUSTFS_ACCESS_KEY=fileservice
RUSTFS_SECRET_KEY=fileservice123
RUSTFS_BUCKET=platform-files

# File Service 配置
FILE_SERVICE_PORT=8089
FILE_SERVICE_PROFILE=docker

# 网络配置
NETWORK_NAME=file-service-network
```

### 3. 启动服务

使用 Docker Compose 启动所有服务：

```bash
# Linux/Mac
./scripts/start.sh

# Windows PowerShell
docker-compose up -d
```

或手动启动：

```bash
docker-compose up -d
```

### 4. 验证服务

检查服务状态：

```bash
docker-compose ps
```

预期输出：

```
NAME                    STATUS              PORTS
file-service-postgres   Up 30 seconds       0.0.0.0:5432->5432/tcp
file-service-rustfs     Up 30 seconds       0.0.0.0:9001->9001/tcp, 0.0.0.0:9002->9002/tcp
file-service-app        Up 10 seconds       0.0.0.0:8089->8089/tcp
```

### 5. 健康检查

检查 File Service 健康状态：

```bash
curl http://localhost:8089/actuator/health
```

预期响应：

```json
{
  "status": "UP"
}
```

## 服务详情

### PostgreSQL

**端口**: 5432

**数据库**: `file_service`

**时区配置**: Asia/Shanghai (UTC+8)

**连接信息**:
```
Host: localhost
Port: 5432
Database: file_service
Username: postgres
Password: postgres
Timezone: Asia/Shanghai
```

**连接字符串**:
```
jdbc:postgresql://localhost:5432/file_service
```

**时区环境变量**:
PostgreSQL 容器配置了以下时区环境变量：
- `TZ=Asia/Shanghai`: 设置容器系统时区为 UTC+8
- `PGTZ=Asia/Shanghai`: 设置 PostgreSQL 数据库时区为 UTC+8

这确保所有时间戳操作都使用 UTC+8 本地时间，不进行时区转换。

**验证时区配置**:
```bash
# 检查 PostgreSQL 时区设置
docker exec -it file-service-postgres psql -U postgres -d file_service -c "SHOW timezone;"

# 预期输出: Asia/Shanghai
```

**数据持久化**:
- 数据卷: `file-service-postgres-data`
- 挂载路径: `/var/lib/postgresql/data`

**初始化脚本**:
- 位置: `postgres-init/init-file-service-db.sql`
- 自动执行: 首次启动时自动创建数据库和表

### RustFS/MinIO

**端口**:
- API: 9001
- Console: 9002

**访问信息**:
```
Endpoint: http://localhost:9001
Access Key: fileservice
Secret Key: fileservice123
Bucket: platform-files
```

**Web 控制台**:
- URL: http://localhost:9002
- 用户名: fileservice
- 密码: fileservice123

**数据持久化**:
- 数据卷: `file-service-rustfs-data`
- 挂载路径: `/data`

**Bucket 配置**:
- 默认 Bucket: `platform-files`
- 自动创建: 首次启动时自动创建

### File Service

**端口**: 8089

**API 端点**:
- Base URL: http://localhost:8089
- Health: http://localhost:8089/actuator/health
- Metrics: http://localhost:8089/actuator/metrics

**环境配置**:
- Profile: docker
- 配置文件: `application-docker.yml`

**依赖服务**:
- PostgreSQL: 必需，等待健康检查通过
- RustFS: 必需，等待健康检查通过

## Docker Compose 配置

### 完整配置文件

```yaml
version: '3.8'

services:
  # PostgreSQL 数据库
  postgres:
    image: postgres:${POSTGRES_VERSION:-14}
    container_name: file-service-postgres
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      POSTGRES_DB: ${POSTGRES_DB:-file_service}
      TZ: ${TZ:-Asia/Shanghai}           # 容器系统时区
      PGTZ: ${PGTZ:-Asia/Shanghai}       # PostgreSQL 数据库时区
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres-init:/docker-entrypoint-initdb.d
    networks:
      - file-service-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  # RustFS 对象存储
  rustfs:
    image: rustfs/rustfs:${RUSTFS_VERSION:-latest}
    container_name: file-service-rustfs
    environment:
      RUSTFS_ACCESS_KEY: ${RUSTFS_ACCESS_KEY:-fileservice}
      RUSTFS_SECRET_KEY: ${RUSTFS_SECRET_KEY:-fileservice123}
      RUSTFS_BUCKET: ${RUSTFS_BUCKET:-platform-files}
    ports:
      - "${RUSTFS_PORT:-9001}:9001"
      - "${RUSTFS_CONSOLE_PORT:-9002}:9002"
    volumes:
      - rustfs-data:/data
      - ./rustfs:/config
    networks:
      - file-service-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9001/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # File Service 应用
  file-service:
    build:
      context: ../
      dockerfile: Dockerfile
    container_name: file-service-app
    environment:
      SPRING_PROFILES_ACTIVE: ${FILE_SERVICE_PROFILE:-docker}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-file_service}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-postgres}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      S3_ENDPOINT: http://rustfs:9001
      S3_ACCESS_KEY: ${RUSTFS_ACCESS_KEY:-fileservice}
      S3_SECRET_KEY: ${RUSTFS_SECRET_KEY:-fileservice123}
      S3_BUCKET: ${RUSTFS_BUCKET:-platform-files}
    ports:
      - "${FILE_SERVICE_PORT:-8089}:8089"
    depends_on:
      postgres:
        condition: service_healthy
      rustfs:
        condition: service_healthy
    networks:
      - file-service-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8089/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  postgres-data:
    name: file-service-postgres-data
  rustfs-data:
    name: file-service-rustfs-data

networks:
  file-service-network:
    name: ${NETWORK_NAME:-file-service-network}
    driver: bridge
```

## 常用命令

### 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 启动特定服务
docker-compose up -d postgres
docker-compose up -d rustfs
docker-compose up -d file-service

# 查看启动日志
docker-compose logs -f
```

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v

# 停止特定服务
docker-compose stop file-service
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f file-service
docker-compose logs -f postgres
docker-compose logs -f rustfs

# 查看最近 100 行日志
docker-compose logs --tail=100 file-service
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart file-service
```

### 进入容器

```bash
# 进入 File Service 容器
docker-compose exec file-service sh

# 进入 PostgreSQL 容器
docker-compose exec postgres psql -U postgres -d file_service

# 进入 RustFS 容器
docker-compose exec rustfs sh
```

### 查看服务状态

```bash
# 查看所有服务状态
docker-compose ps

# 查看服务资源使用
docker stats
```

## 数据管理

### 备份数据库

```bash
# 备份数据库
docker-compose exec postgres pg_dump -U postgres file_service > backup.sql

# 或使用 docker exec
docker exec file-service-postgres pg_dump -U postgres file_service > backup.sql
```

### 恢复数据库

```bash
# 恢复数据库
docker-compose exec -T postgres psql -U postgres file_service < backup.sql

# 或使用 docker exec
docker exec -i file-service-postgres psql -U postgres file_service < backup.sql
```

### 清理数据

```bash
# 停止服务并删除数据卷
docker-compose down -v

# 删除特定数据卷
docker volume rm file-service-postgres-data
docker volume rm file-service-rustfs-data
```

## 故障排查

### 服务无法启动

1. **检查端口占用**:

```bash
# Linux/Mac
netstat -tuln | grep 5432
netstat -tuln | grep 9001
netstat -tuln | grep 8089

# Windows
netstat -ano | findstr 5432
netstat -ano | findstr 9001
netstat -ano | findstr 8089
```

2. **检查 Docker 资源**:

```bash
docker system df
docker system prune
```

3. **查看详细日志**:

```bash
docker-compose logs --tail=100 file-service
```

### 数据库连接失败

1. **检查 PostgreSQL 健康状态**:

```bash
docker-compose ps postgres
docker-compose logs postgres
```

2. **手动测试连接**:

```bash
docker-compose exec postgres psql -U postgres -d file_service -c "SELECT 1"
```

3. **检查网络连接**:

```bash
docker network inspect file-service-network
```

### RustFS 无法访问

1. **检查 RustFS 状态**:

```bash
docker-compose ps rustfs
docker-compose logs rustfs
```

2. **测试 API 端点**:

```bash
curl http://localhost:9001/health
```

3. **访问 Web 控制台**:

打开浏览器访问 http://localhost:9002

### File Service 启动失败

1. **检查依赖服务**:

```bash
docker-compose ps
```

确保 PostgreSQL 和 RustFS 都处于 healthy 状态。

2. **检查应用日志**:

```bash
docker-compose logs --tail=200 file-service
```

3. **检查配置**:

```bash
docker-compose exec file-service env | grep SPRING
docker-compose exec file-service env | grep S3
```

## 性能优化

### 调整资源限制

在 `docker-compose.yml` 中添加资源限制：

```yaml
services:
  file-service:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 调整 PostgreSQL 配置

创建 `postgres/postgresql.conf`：

```conf
max_connections = 100
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 2621kB
min_wal_size = 1GB
max_wal_size = 4GB
```

挂载配置文件：

```yaml
services:
  postgres:
    volumes:
      - ./postgres/postgresql.conf:/etc/postgresql/postgresql.conf
    command: postgres -c config_file=/etc/postgresql/postgresql.conf
```

## 监控与日志

### 启用 Prometheus 监控

在 `docker-compose.yml` 中添加 Prometheus：

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    container_name: file-service-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    networks:
      - file-service-network

volumes:
  prometheus-data:
```

### 日志收集

配置日志驱动：

```yaml
services:
  file-service:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## 生产环境部署

### 安全加固

1. **使用强密码**:

```env
POSTGRES_PASSWORD=<strong-random-password>
RUSTFS_SECRET_KEY=<strong-random-key>
```

2. **限制网络访问**:

```yaml
services:
  postgres:
    ports:
      - "127.0.0.1:5432:5432"  # 只允许本地访问
```

3. **启用 SSL/TLS**:

配置 PostgreSQL SSL 和 RustFS HTTPS。

### 高可用配置

1. **PostgreSQL 主从复制**
2. **RustFS 集群模式**
3. **File Service 多实例部署**

### 备份策略

1. **定时备份数据库**:

```bash
# 添加到 crontab
0 2 * * * docker exec file-service-postgres pg_dump -U postgres file_service | gzip > /backup/file_service_$(date +\%Y\%m\%d).sql.gz
```

2. **备份对象存储**:

使用 RustFS/MinIO 的备份工具。

## 常见问题

### Q: 如何更新 File Service 版本？

A: 
```bash
# 1. 停止服务
docker-compose down

# 2. 拉取新镜像或重新构建
docker-compose build file-service

# 3. 启动服务
docker-compose up -d
```

### Q: 如何迁移数据到新环境？

A:
```bash
# 1. 备份旧环境数据
docker-compose exec postgres pg_dump -U postgres file_service > backup.sql

# 2. 在新环境恢复
docker-compose exec -T postgres psql -U postgres file_service < backup.sql

# 3. 同步对象存储数据
# 使用 RustFS/MinIO 的同步工具
```

### Q: 如何扩展存储容量？

A: 
- PostgreSQL: 调整数据卷大小或迁移到更大的磁盘
- RustFS: 添加更多存储节点或扩展数据卷

### Q: 如何监控服务健康？

A:
```bash
# 使用健康检查端点
curl http://localhost:8089/actuator/health

# 查看 Docker 健康状态
docker-compose ps
```

## 相关文档

- [File Service README](../README.md)
- [API 文档](../API.md)
- [集成指南](../INTEGRATION.md)
- [检查点验证指南](CHECKPOINT_VERIFICATION_GUIDE.md)

## 支持

如有问题，请：
1. 查看日志: `docker-compose logs -f`
2. 检查健康状态: `docker-compose ps`
3. 参考故障排查章节
4. 提交 Issue
