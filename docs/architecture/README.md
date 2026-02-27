# File-Service 架构文档

## 项目概述

File-Service 是一个独立的文件服务微服务，提供多种文件上传方式、访问控制、多租户隔离和审计追踪能力。基于 Spring Boot 3.2.4 构建，使用 S3 兼容协议对接多种存储后端。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.4 |
| 语言 | Java | 17 |
| 数据库 | PostgreSQL | 42.7.1 |
| ORM | MyBatis | 3.0.3 |
| 缓存 | Redis (Redisson) | 3.27.2 |
| 对象存储 | AWS SDK v2 (S3 兼容) | 2.25.0 |
| 图片处理 | Thumbnailator / WebP ImageIO | 0.4.20 / 0.1.6 |
| ID 生成 | UUID Creator (UUIDv7) | 5.3.3 |
| 注册中心 | Nacos | — |
| 监控 | Micrometer Prometheus | — |

## 模块结构

```
file-service/                          # 根项目
├── file-service/                      # 核心微服务
├── file-service-client/               # Java 客户端 SDK
├── file-service-spring-boot-starter/  # Spring Boot Starter 自动配置
├── examples/file-service-example/     # 示例应用
├── docker/                            # Docker Compose（MinIO/RustFS/PostgreSQL/监控）
├── scripts/                           # 脚本工具
└── docs/                              # 项目文档
```

## 文档索引

| 文档 | 说明 |
|------|------|
| [分层架构](./layered-architecture.md) | 代码分层、包结构、职责划分 |
| [API 接口](./api-endpoints.md) | 完整的 REST API 清单 |
| [核心业务流程](./core-workflows.md) | 上传、秒传、断点续传、访问控制等关键流程 |
| [数据模型](./data-model.md) | 领域模型、数据库表结构、状态机 |
| [配置与中间件](./configuration.md) | 配置属性、中间件依赖、缓存设计 |
