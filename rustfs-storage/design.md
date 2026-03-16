# Design Document

## Overview

本设计文档描述了为 blog-upload 服务实现 S3 兼容存储的技术方案。通过实现 `S3StorageService` 类，使用 AWS S3 SDK 与 RustFS（或其他 S3 兼容存储服务如 MinIO）交互。

本设计还包括：
1. **文件元数据存储**：使用数据库表记录上传文件的元信息
2. **大文件断点续传**：基于 S3 Multipart Upload API 实现分片上传和断点恢复

RustFS 是 100% S3 兼容的分布式对象存储，推荐使用官方 AWS S3 SDK 进行访问。Docker 环境中已配置好 RustFS 服务（端口 9000/9001）。

## Architecture

### 组件关系图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           blog-upload Service                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐    ┌─────────────────────┐                         │
│  │  UploadController   │    │ MultipartController │ ◄─── NEW                │
│  └──────────┬──────────┘    └──────────┬──────────┘                         │
│             │                          │                                     │
│  ┌──────────▼──────────┐    ┌──────────▼──────────┐                         │
│  │UploadApplicationSvc │    │MultipartUploadSvc   │ ◄─── NEW                │
│  └──────────┬──────────┘    └──────────┬──────────┘                         │
│             │                          │                                     │
│  ┌──────────▼──────────────────────────▼──────────┐                         │
│  │              StorageService                     │ ◄─── Interface          │
│  └──────────────────────┬─────────────────────────┘                         │
│                         │                                                    │
│    ┌────────────────────┼────────────────────┐                              │
│    │                    │                    │                              │
│  ┌─▼───────────┐  ┌─────▼────────┐  ┌───────▼───────┐                      │
│  │LocalStorage │  │ S3Storage    │  │FileRecord     │ ◄─── NEW             │
│  │Service      │  │ Service      │  │Repository     │                      │
│  └─────────────┘  └──────┬───────┘  └───────┬───────┘                      │
│                          │                  │                               │
└──────────────────────────┼──────────────────┼───────────────────────────────┘
                           │                  │
                  ┌────────▼────────┐  ┌──────▼──────┐
                  │  RustFS / MinIO │  │  PostgreSQL │
                  │  Object Storage │  │  (Metadata) │
                  └─────────────────┘  └─────────────┘
```

### 条件装配策略

```yaml
storage.type=local  → LocalStorageService (默认)
storage.type=s3     → S3StorageService
```

## Detailed Design

### 1. S3StorageService 类设计

```java
package com.blog.upload.infrastructure.storage;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    
    private final S3Client s3Client;
    private final S3Properties properties;
    
    // 构造函数注入 S3Client 和配置
    public S3StorageService(S3Properties properties) {
        this.properties = properties;
        this.s3Client = buildS3Client(properties);
    }
    
    @PostConstruct
    public void init() {
        ensureBucketExists();
    }
    
    @Override
    public String upload(byte[] data, String path) {
        return upload(data, path, "application/octet-stream");
    }
    
    @Override
    public String upload(byte[] data, String path, String contentType) {
        // 使用 PutObjectRequest 上传到 S3
        // 返回 CDN URL 或 S3 URL
    }
    
    @Override
    public void delete(String path) {
        // 使用 DeleteObjectRequest 删除对象
    }
    
    @Override
    public String getUrl(String path) {
        // 如果配置了 CDN domain，返回 CDN URL
        // 否则返回 S3 endpoint URL
    }
    
    @Override
    public boolean exists(String path) {
        // 使用 HeadObjectRequest 检查对象是否存在
    }
    
    private S3Client buildS3Client(S3Properties props) {
        // 构建 S3Client，配置 endpoint、credentials、region
        // RustFS 100% 兼容 S3 API，使用标准 AWS SDK
    }
    
    private void ensureBucketExists() {
        // 检查 bucket 是否存在，不存在则创建
    }
}
```

### 2. S3Properties 配置类

```java
package com.blog.upload.infrastructure.config;

@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {
    
    /**
     * S3 endpoint URL (e.g., http://localhost:9000 for RustFS/MinIO)
     */
    private String endpoint;
    
    /**
     * S3 access key
     */
    private String accessKey;
    
    /**
     * S3 secret key
     */
    private String secretKey;
    
    /**
     * S3 bucket name
     */
    private String bucket;
    
    /**
     * S3 region (default: us-east-1)
     */
    private String region = "us-east-1";
    
    /**
     * Optional CDN domain for public file access
     */
    private String cdnDomain;
    
    /**
     * 是否使用 path-style access (RustFS/MinIO 需要)
     */
    private boolean pathStyleAccess = true;
}
```

### 3. Maven 依赖

在 `blog-upload/pom.xml` 中添加 AWS S3 SDK：

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
</dependency>
```

### 4. 数据库表设计

#### 4.1 存储对象表 (storage_objects) - 用于文件去重

```sql
CREATE TABLE storage_objects (
    id              VARCHAR(36) PRIMARY KEY,  -- UUIDv7 (时间有序)
    file_hash       VARCHAR(64) NOT NULL,     -- MD5 或 SHA256
    hash_algorithm  VARCHAR(16) NOT NULL DEFAULT 'MD5',
    storage_path    VARCHAR(512) NOT NULL,    -- S3 存储路径
    file_size       BIGINT NOT NULL,
    content_type    VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    reference_count INT NOT NULL DEFAULT 1,   -- 引用计数
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE INDEX uk_file_hash (file_hash),
    INDEX idx_reference_count (reference_count),
    INDEX idx_created_at (created_at)
);
```

#### 4.2 文件记录表 (file_records)

```sql
CREATE TABLE file_records (
    id                VARCHAR(36) PRIMARY KEY,  -- UUIDv7 (时间有序)
    user_id           BIGINT NOT NULL,
    storage_object_id VARCHAR(36) NOT NULL,     -- 逻辑关联 storage_objects.id
    original_name     VARCHAR(255) NOT NULL,
    file_size         BIGINT NOT NULL,
    content_type      VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    file_hash         VARCHAR(64) NOT NULL,     -- 冗余存储，便于查询
    access_level      VARCHAR(16) NOT NULL DEFAULT 'public',  -- public, private
    status            VARCHAR(32) NOT NULL DEFAULT 'completed',  -- completed, deleted
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_storage_object_id (storage_object_id),
    INDEX idx_file_hash (file_hash),
    INDEX idx_user_hash (user_id, file_hash),
    INDEX idx_access_level (access_level),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);
```

#### 4.3 上传任务表 (upload_tasks)

```sql
CREATE TABLE upload_tasks (
    id              VARCHAR(36) PRIMARY KEY,  -- UUIDv7 (时间有序)
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_hash       VARCHAR(64),              -- 用于断点续传匹配
    storage_path    VARCHAR(512) NOT NULL,
    upload_id       VARCHAR(256) NOT NULL,    -- S3 Multipart Upload ID
    total_parts     INT NOT NULL,
    chunk_size      INT NOT NULL DEFAULT 5242880,  -- 5MB
    status          VARCHAR(32) NOT NULL DEFAULT 'uploading',  -- uploading, completed, aborted, expired
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL,       -- 任务过期时间
    
    INDEX idx_user_id (user_id),
    INDEX idx_file_hash (file_hash),
    INDEX idx_upload_id (upload_id),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at)
);
```

> **设计说明**：
> - 所有表的主键使用 UUIDv7 (VARCHAR(36))，时间有序的 UUID
>   - UUIDv7 包含时间戳前缀，天然支持按时间排序
>   - 适合分页查询和范围查询
>   - 分布式环境下无需协调即可生成唯一 ID
> - 不使用物理外键约束，通过应用层逻辑保证数据一致性
> - 当前生产链路以 `upload_tasks` 作为 upload session 的持久化视图，分片完成状态由 file-core upload session 生命周期统一承载
> - Java 实现：使用 `com.github.f4b6a3:uuid-creator` 库生成 UUIDv7

### 5. 分片上传流程设计

#### 5.1 初始化上传 (Initiate Multipart Upload)

```
Client                    Upload Service                S3/RustFS
   │                            │                           │
   │  POST /multipart/init      │                           │
   │  {fileName, fileSize,      │                           │
   │   fileHash, contentType}   │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  检查是否存在相同 hash     │
   │                            │  的未完成任务              │
   │                            │                           │
   │                            │  CreateMultipartUpload    │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  uploadId                 │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │                            │  创建 upload_task 记录    │
   │                            │                           │
   │  {taskId, uploadId,        │                           │
   │   chunkSize, totalParts,   │                           │
   │   completedParts[]}        │                           │
   │ ◄───────────────────────── │                           │
```

#### 5.2 上传分片 (Upload Part)

```
Client                    Upload Service                S3/RustFS
   │                            │                           │
   │  PUT /multipart/{taskId}/  │                           │
   │      parts/{partNumber}    │                           │
   │  [binary data]             │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  UploadPart               │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  ETag                     │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │                            │  记录分片完成状态         │
   │                            │  (session/cache)         │
   │                            │                           │
   │  {partNumber, etag}        │                           │
   │ ◄───────────────────────── │                           │
```

#### 5.3 完成上传 (Complete Multipart Upload)

```
Client                    Upload Service                S3/RustFS
   │                            │                           │
   │  POST /multipart/{taskId}/ │                           │
   │       complete             │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  汇总已完成分片状态       │
   │                            │                           │
   │                            │  CompleteMultipartUpload  │
   │                            │  (parts: [{partNum, etag}])│
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  success                  │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │                            │  创建 file_record         │
   │                            │  更新 task status         │
   │                            │                           │
   │  {fileId, url}             │                           │
   │ ◄───────────────────────── │                           │
```

### 6. 断点续传设计

#### 6.1 断点续传流程

```
Client                    Upload Service                Database
   │                            │                           │
   │  POST /multipart/init      │                           │
   │  {fileName, fileSize,      │                           │
   │   fileHash}                │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  查询相同 fileHash 的     │
   │                            │  未完成任务               │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  返回已存在的任务         │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │                            │  查询已完成的分片         │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  返回分片列表             │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │  {taskId, uploadId,        │                           │
   │   completedParts: [1,2,3]} │                           │
   │ ◄───────────────────────── │                           │
   │                            │                           │
   │  客户端只上传缺失的分片    │                           │
```

#### 6.2 文件 Hash 计算

- 客户端在上传前计算文件的 MD5 或 SHA256 hash
- 服务端使用 hash 匹配已存在的未完成上传任务
- 相同用户 + 相同 hash = 可以续传

### 7. API 设计

#### 7.1 分片上传 API

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/multipart/init` | 初始化分片上传 |
| PUT | `/api/v1/multipart/{taskId}/parts/{partNumber}` | 上传单个分片 |
| POST | `/api/v1/multipart/{taskId}/complete` | 完成分片上传 |
| DELETE | `/api/v1/multipart/{taskId}` | 取消/中止上传 |
| GET | `/api/v1/multipart/{taskId}/progress` | 查询上传进度 |
| GET | `/api/v1/multipart/tasks` | 列出用户的上传任务 |

#### 7.2 请求/响应示例

**初始化上传请求:**
```json
POST /api/v1/multipart/init
{
    "fileName": "large-video.mp4",
    "fileSize": 104857600,
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "contentType": "video/mp4"
}
```

**初始化上传响应:**
```json
{
    "code": 200,
    "data": {
        "taskId": "1234567890",
        "uploadId": "abc123...",
        "chunkSize": 5242880,
        "totalParts": 20,
        "completedParts": [1, 2, 3]  // 断点续传时返回已完成的分片
    }
}
```

**上传进度响应:**
```json
{
    "code": 200,
    "data": {
        "taskId": "1234567890",
        "totalParts": 20,
        "completedParts": 15,
        "uploadedBytes": 78643200,
        "totalBytes": 104857600,
        "percentage": 75
    }
}
```

### 8. 配置项

```yaml
storage:
  multipart:
    enabled: true
    threshold: 10485760        # 10MB - 超过此大小使用分片上传
    chunk-size: 5242880        # 5MB - 每个分片大小
    max-parts: 10000           # 最大分片数
    task-expire-hours: 24      # 任务过期时间（小时）
    cleanup-cron: "0 0 * * * *"  # 每小时清理过期任务
  
  # 文件类型限制
  allowed-types:
    images:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - image/svg+xml
    videos:
      - video/mp4
      - video/webm
      - video/quicktime
    documents:
      - application/pdf
      - application/msword
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
  allowed-extensions:
    - jpg
    - jpeg
    - png
    - gif
    - webp
    - svg
    - mp4
    - webm
    - mov
    - pdf
    - doc
    - docx
  max-file-size: 104857600     # 100MB 最大文件大小
  
  # 访问控制
  access:
    private-url-expire-seconds: 3600  # 私有文件临时 URL 过期时间（1小时）
    presigned-url-expire-seconds: 900 # 预签名上传 URL 过期时间（15分钟）
```

### 9. 秒传功能设计

#### 9.1 秒传流程

```
Client                    Upload Service                Database
   │                            │                           │
   │  POST /upload/check        │                           │
   │  {fileHash, fileSize}      │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  查询 storage_objects     │
   │                            │  WHERE file_hash = ?      │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  存在相同 hash 的文件     │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │                            │  查询用户是否已有该文件   │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │  {instantUpload: true,     │                           │
   │   fileId, url}             │                           │
   │ ◄───────────────────────── │                           │
```

#### 9.2 秒传 API

```
POST /api/v1/upload/check
```

**请求:**
```json
{
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "fileSize": 1048576,
    "fileName": "example.jpg"
}
```

**响应 (秒传成功):**
```json
{
    "code": 200,
    "data": {
        "instantUpload": true,
        "fileId": "01912345-6789-7abc-def0-123456789abc",
        "url": "https://cdn.example.com/uploads/2026/01/16/123/file.jpg"
    }
}
```

**响应 (需要上传):**
```json
{
    "code": 200,
    "data": {
        "instantUpload": false,
        "needUpload": true
    }
}
```

### 10. 文件去重与引用计数设计

#### 10.1 去重策略

```
┌─────────────────────────────────────────────────────────────────┐
│                        文件上传流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 计算文件 hash (客户端或服务端)                               │
│                    │                                             │
│                    ▼                                             │
│  2. 查询 storage_objects 表是否存在相同 hash                     │
│                    │                                             │
│         ┌─────────┴─────────┐                                   │
│         │                   │                                   │
│         ▼                   ▼                                   │
│     存在                  不存在                                 │
│         │                   │                                   │
│         ▼                   ▼                                   │
│  3a. reference_count++   3b. 上传到 S3                          │
│      创建 file_record        创建 storage_object                │
│      (复用 storage_object)   创建 file_record                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 10.2 删除流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        文件删除流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 更新 file_record.status = 'deleted'                         │
│                    │                                             │
│                    ▼                                             │
│  2. storage_object.reference_count--                            │
│                    │                                             │
│                    ▼                                             │
│  3. 检查 reference_count                                        │
│                    │                                             │
│         ┌─────────┴─────────┐                                   │
│         │                   │                                   │
│         ▼                   ▼                                   │
│     > 0                   = 0                                   │
│         │                   │                                   │
│         ▼                   ▼                                   │
│     保留 S3 对象         删除 S3 对象                           │
│                          删除 storage_object 记录               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 11. 预签名 URL 直传设计

#### 11.1 预签名上传流程

```
Client                    Upload Service                S3/RustFS
   │                            │                           │
   │  POST /upload/presign      │                           │
   │  {fileName, fileSize,      │                           │
   │   contentType, fileHash}   │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  生成存储路径              │
   │                            │  生成预签名 URL            │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  presignedUrl             │
   │                            │ ◄─────────────────────────│
   │                            │                           │
   │  {presignedUrl, storagePath,│                          │
   │   expiresAt}               │                           │
   │ ◄───────────────────────── │                           │
   │                            │                           │
   │  PUT presignedUrl          │                           │
   │  [binary data]             │                           │
   │ ──────────────────────────────────────────────────────►│
   │                            │                           │
   │  200 OK                    │                           │
   │ ◄──────────────────────────────────────────────────────│
   │                            │                           │
   │  POST /upload/confirm      │                           │
   │  {storagePath, fileHash}   │                           │
   │ ─────────────────────────► │                           │
   │                            │                           │
   │                            │  验证文件存在              │
   │                            │ ─────────────────────────►│
   │                            │                           │
   │                            │  创建 file_record         │
   │                            │                           │
   │  {fileId, url}             │                           │
   │ ◄───────────────────────── │                           │
```

#### 11.2 预签名 API

**获取预签名 URL:**
```
POST /api/v1/upload/presign
```

**请求:**
```json
{
    "fileName": "large-video.mp4",
    "fileSize": 104857600,
    "contentType": "video/mp4",
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "accessLevel": "public"
}
```

**响应:**
```json
{
    "code": 200,
    "data": {
        "presignedUrl": "https://s3.example.com/bucket/path?X-Amz-Signature=...",
        "storagePath": "2026/01/16/123/01912345-6789-7abc.mp4",
        "expiresAt": "2026-01-16T12:15:00Z",
        "method": "PUT",
        "headers": {
            "Content-Type": "video/mp4"
        }
    }
}
```

**确认上传完成:**
```
POST /api/v1/upload/confirm
```

**请求:**
```json
{
    "storagePath": "2026/01/16/123/01912345-6789-7abc.mp4",
    "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
    "originalName": "large-video.mp4"
}
```

**响应:**
```json
{
    "code": 200,
    "data": {
        "fileId": "01912345-6789-7abc-def0-123456789abc",
        "url": "https://cdn.example.com/uploads/2026/01/16/123/01912345-6789-7abc.mp4"
    }
}
```

### 12. 存储路径策略

#### 12.1 路径格式

```
{bucket}/{year}/{month}/{day}/{userId}/{fileId}.{ext}
```

**示例:**
```
blog-uploads/2026/01/16/12345/01912345-6789-7abc-def0-123456789abc.jpg
```

#### 12.2 路径生成逻辑

```java
public String generateStoragePath(Long userId, String originalName) {
    LocalDate now = LocalDate.now();
    String fileId = UuidCreator.getTimeOrderedEpoch().toString();
    String extension = getExtension(originalName);
    
    return String.format("%d/%02d/%02d/%d/%s.%s",
        now.getYear(),
        now.getMonthValue(),
        now.getDayOfMonth(),
        userId,
        fileId,
        extension
    );
}
```

### 13. 文件类型验证设计

#### 13.1 验证流程

```java
public void validateFileType(String fileName, String contentType, byte[] fileHeader) {
    // 1. 验证文件扩展名
    String extension = getExtension(fileName);
    if (!allowedExtensions.contains(extension.toLowerCase())) {
        throw new BusinessException("不支持的文件扩展名: " + extension);
    }
    
    // 2. 验证 Content-Type
    if (!isAllowedContentType(contentType)) {
        throw new BusinessException("不支持的文件类型: " + contentType);
    }
    
    // 3. 验证文件魔数 (Magic Number) - 防止伪造扩展名
    String detectedType = detectFileType(fileHeader);
    if (!isTypeMatch(contentType, detectedType)) {
        throw new BusinessException("文件类型与内容不匹配");
    }
}
```

#### 13.2 文件魔数检测

| 文件类型 | 魔数 (Hex) |
|----------|------------|
| JPEG | FF D8 FF |
| PNG | 89 50 4E 47 |
| GIF | 47 49 46 38 |
| PDF | 25 50 44 46 |
| MP4 | 00 00 00 xx 66 74 79 70 |
| WebP | 52 49 46 46 xx xx xx xx 57 45 42 50 |

### 14. 文件访问权限控制设计

#### 14.1 访问级别

| 级别 | 说明 | URL 类型 |
|------|------|----------|
| public | 公开访问 | 永久 CDN/S3 URL |
| private | 私有访问 | 临时预签名 URL |

#### 14.2 获取文件 URL 流程

```java
public String getFileUrl(String fileId, Long requestUserId) {
    FileRecord record = fileRecordRepository.findById(fileId)
        .orElseThrow(() -> new BusinessException("文件不存在"));
    
    StorageObject storageObject = storageObjectRepository.findById(record.getStorageObjectId())
        .orElseThrow(() -> new BusinessException("存储对象不存在"));
    
    if (record.getAccessLevel() == AccessLevel.PUBLIC) {
        // 公开文件：返回永久 URL
        return buildPublicUrl(storageObject.getStoragePath());
    } else {
        // 私有文件：验证权限后返回临时 URL
        if (!record.getUserId().equals(requestUserId)) {
            throw new BusinessException("无权访问该文件");
        }
        return generatePresignedGetUrl(storageObject.getStoragePath(), 
            accessProperties.getPrivateUrlExpireSeconds());
    }
}
```

#### 14.3 私有文件访问 API

```
GET /api/v1/files/{fileId}/url
```

**响应 (公开文件):**
```json
{
    "code": 200,
    "data": {
        "url": "https://cdn.example.com/uploads/2026/01/16/123/file.jpg",
        "permanent": true
    }
}
```

**响应 (私有文件):**
```json
{
    "code": 200,
    "data": {
        "url": "https://s3.example.com/bucket/path?X-Amz-Signature=...",
        "permanent": false,
        "expiresAt": "2026-01-16T13:00:00Z"
    }
}
```

### 4. Docker 环境配置（已存在）

Docker 环境中已配置好 RustFS 服务（100% S3 兼容）：

```yaml
# docker/docker-compose.yml 中已有配置
rustfs:
  image: rustfs/rustfs:latest
  container_name: blog-rustfs
  environment:
    - RUSTFS_ACCESS_KEY=${RUSTFS_ROOT_USER:-admin}
    - RUSTFS_SECRET_KEY=${RUSTFS_ROOT_PASSWORD:-admin123456}
    - RUSTFS_CONSOLE_ADDRESS=:9101
  ports:
    - "9100:9000"   # S3 API port (外部9100 -> 内部9000)
    - "9101:9101"   # Console port
  volumes:
    - rustfs_data:/data
```

### 5. 环境变量配置

```bash
# 已在 docker/.env 中配置
RUSTFS_ROOT_USER=admin
RUSTFS_ROOT_PASSWORD=admin123456
RUSTFS_API_PORT=9100
RUSTFS_CONSOLE_PORT=9101

# blog-upload 服务配置
STORAGE_TYPE=s3
S3_ENDPOINT=http://rustfs:9000  # Docker 内部网络使用内部端口
# 或本地开发时使用: http://localhost:9100
S3_ACCESS_KEY=admin
S3_SECRET_KEY=admin123456
S3_BUCKET=blog-uploads
S3_REGION=us-east-1
S3_CDN_DOMAIN=
```

## Error Handling

| 场景 | 处理方式 |
|------|----------|
| S3 连接失败 | 启动时抛出异常，服务无法启动 |
| Bucket 不存在 | 自动创建 bucket |
| Bucket 创建失败 | 抛出 BusinessException |
| 上传失败 | 抛出 BusinessException，包含错误详情 |
| 删除失败 | 记录日志，抛出 BusinessException |
| 文件不存在检查 | 返回 false，不抛异常 |
| 分片上传失败 | 记录失败分片，支持重试 |
| 任务过期 | 返回 404，提示重新上传 |
| 分片顺序错误 | 返回 400，提示正确的分片号 |
| 文件 hash 不匹配 | 返回 400，提示文件已变更 |

## Correctness Properties

### Property 1: 存储类型切换
- **Given**: storage.type 配置为 "s3"
- **When**: 应用启动
- **Then**: S3StorageService 被注入，LocalStorageService 不被创建

### Property 2: 文件上传完整性
- **Given**: 一个有效的文件数据和路径
- **When**: 调用 upload 方法
- **Then**: 文件被存储到 S3，返回的 URL 可以访问该文件

### Property 3: Bucket 自动创建
- **Given**: 配置的 bucket 不存在
- **When**: S3StorageService 初始化
- **Then**: bucket 被自动创建

### Property 4: CDN URL 优先
- **Given**: 配置了 cdnDomain
- **When**: 调用 getUrl 方法
- **Then**: 返回 CDN domain 格式的 URL

### Property 5: 错误隔离
- **Given**: S3 操作失败
- **When**: 捕获到 S3Exception
- **Then**: 转换为 BusinessException，不泄露 S3 内部错误

### Property 6: 文件记录一致性
- **Given**: 文件上传成功
- **When**: 上传完成
- **Then**: file_records 表中存在对应记录，status 为 "completed"

### Property 7: 断点续传匹配
- **Given**: 存在未完成的上传任务
- **When**: 相同用户使用相同 file_hash 初始化上传
- **Then**: 返回已存在的任务和已完成的分片列表

### Property 8: 分片上传完整性
- **Given**: 所有分片上传完成
- **When**: 调用 complete 接口
- **Then**: S3 合并所有分片，file_records 创建记录，upload_task 状态更新为 "completed"

### Property 9: 过期任务清理
- **Given**: 上传任务超过过期时间
- **When**: 清理任务执行
- **Then**: S3 中止 multipart upload，数据库记录状态更新为 "expired"

### Property 10: 秒传功能
- **Given**: 存在相同 file_hash 的已完成文件
- **When**: 用户请求上传相同 hash 的文件
- **Then**: 立即返回已存在文件的 URL，不进行实际上传

### Property 11: 文件去重
- **Given**: 两个用户上传相同内容的文件
- **When**: 第二个用户上传完成
- **Then**: storage_objects 表只有一条记录，reference_count = 2

### Property 12: 引用计数删除
- **Given**: storage_object.reference_count = 1
- **When**: 唯一引用的 file_record 被删除
- **Then**: S3 对象被删除，storage_object 记录被删除

### Property 13: 预签名 URL 有效性
- **Given**: 生成预签名上传 URL
- **When**: 在过期时间内使用该 URL 上传
- **Then**: 上传成功

### Property 14: 预签名 URL 过期
- **Given**: 生成预签名上传 URL
- **When**: 超过过期时间后使用该 URL
- **Then**: 上传失败，返回 403 错误

### Property 15: 文件类型验证
- **Given**: 配置了允许的文件类型列表
- **When**: 上传不允许的文件类型
- **Then**: 返回 400 错误，拒绝上传

### Property 16: 文件魔数验证
- **Given**: 文件扩展名为 .jpg
- **When**: 文件内容实际是 .exe
- **Then**: 返回 400 错误，拒绝上传

### Property 17: 公开文件访问
- **Given**: file_record.access_level = 'public'
- **When**: 任何用户请求文件 URL
- **Then**: 返回永久公开 URL

### Property 18: 私有文件访问控制
- **Given**: file_record.access_level = 'private'
- **When**: 非文件所有者请求文件 URL
- **Then**: 返回 403 错误

### Property 19: 私有文件临时 URL
- **Given**: file_record.access_level = 'private'
- **When**: 文件所有者请求文件 URL
- **Then**: 返回带过期时间的临时预签名 URL

## File Changes

| 文件 | 操作 | 说明 |
|------|------|------|
| `blog-upload/pom.xml` | 修改 | 添加 AWS S3 SDK、MyBatis 依赖和 uuid-creator 依赖 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/config/S3Properties.java` | 新增 | S3 配置属性类 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/config/MultipartProperties.java` | 新增 | 分片上传配置属性类 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/config/FileTypeProperties.java` | 新增 | 文件类型限制配置类 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/config/AccessProperties.java` | 新增 | 访问控制配置类 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/storage/S3StorageService.java` | 新增 | S3 存储服务实现 |
| `blog-upload/src/main/java/com/blog/upload/domain/model/StorageObject.java` | 新增 | 存储对象领域模型（去重） |
| `blog-upload/src/main/java/com/blog/upload/domain/model/FileRecord.java` | 新增 | 文件记录领域模型 |
| `blog-upload/src/main/java/com/blog/upload/domain/model/AccessLevel.java` | 新增 | 访问级别枚举 |
| `blog-upload/src/main/java/com/blog/upload/domain/model/UploadTask.java` | 新增 | 上传任务领域模型 |
| `blog-upload/src/main/java/com/blog/upload/domain/model/UploadPart.java` | 新增 | 上传分片领域模型 |
| `blog-upload/src/main/java/com/blog/upload/domain/repository/StorageObjectRepository.java` | 新增 | 存储对象仓储接口 |
| `blog-upload/src/main/java/com/blog/upload/domain/repository/FileRecordRepository.java` | 新增 | 文件记录仓储接口 |
| `blog-upload/src/main/java/com/blog/upload/domain/repository/UploadTaskRepository.java` | 新增 | 历史上传任务仓储接口，现已退化为 compat/test-only |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/repository/*` | 新增 | 仓储实现和 Mapper |
| `blog-upload/src/main/java/com/blog/upload/application/service/MultipartUploadService.java` | 新增 | 分片上传应用服务 |
| `blog-upload/src/main/java/com/blog/upload/application/service/InstantUploadService.java` | 新增 | 秒传服务 |
| `blog-upload/src/main/java/com/blog/upload/application/service/PresignedUrlService.java` | 新增 | 预签名 URL 服务 |
| `blog-upload/src/main/java/com/blog/upload/application/service/FileTypeValidator.java` | 新增 | 文件类型验证服务 |
| `blog-upload/src/main/java/com/blog/upload/application/service/FileAccessService.java` | 新增 | 文件访问控制服务 |
| `blog-upload/src/main/java/com/blog/upload/interfaces/controller/MultipartController.java` | 新增 | 分片上传控制器 |
| `blog-upload/src/main/java/com/blog/upload/interfaces/controller/PresignedController.java` | 新增 | 预签名 URL 控制器 |
| `blog-upload/src/main/java/com/blog/upload/interfaces/controller/FileController.java` | 新增 | 文件访问控制器 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/util/FileTypeDetector.java` | 新增 | 文件魔数检测工具 |
| `blog-upload/src/main/java/com/blog/upload/infrastructure/util/StoragePathGenerator.java` | 新增 | 存储路径生成工具 |
| `blog-upload/src/main/resources/application.yml` | 修改 | 更新 S3、分片上传、文件类型、访问控制配置 |
| `blog-migration/src/main/resources/db/migration/upload/V1__create_upload_tables.sql` | 新增 | 数据库迁移脚本 |

### Maven 依赖

```xml
<!-- UUIDv7 生成器 -->
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>5.3.7</version>
</dependency>

<!-- Apache Tika (文件类型检测) -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>
```

### UUIDv7 使用示例

```java
import com.github.f4b6a3.uuid.UuidCreator;

// 生成 UUIDv7 (时间有序)
String id = UuidCreator.getTimeOrderedEpoch().toString();
```

注：Docker 基础设施已配置好，无需修改 docker-compose.yml。

## Testing Strategy

1. **单元测试**: 使用 Mock S3Client 测试 S3StorageService 逻辑
2. **集成测试**: 使用 Docker 中的 RustFS 服务进行真实 S3 操作测试
3. **分片上传测试**: 测试分片上传、断点续传、任务过期清理
4. **手动测试**: 通过 API 测试脚本验证上传功能，确认文件存储到 RustFS

### 测试场景

| 场景 | 测试方法 |
|------|----------|
| 小文件直接上传 | 单元测试 + 集成测试 |
| 大文件分片上传 | 集成测试 |
| 断点续传 | 集成测试（模拟中断） |
| 任务过期清理 | 单元测试 + 定时任务测试 |
| 并发上传同一文件 | 集成测试 |
| 文件 hash 校验 | 单元测试 |
| 秒传功能 | 单元测试 + 集成测试 |
| 文件去重 | 集成测试（多用户上传相同文件） |
| 引用计数删除 | 集成测试 |
| 预签名 URL 上传 | 集成测试 |
| 预签名 URL 过期 | 集成测试 |
| 文件类型验证 | 单元测试 |
| 文件魔数检测 | 单元测试 |
| 公开文件访问 | 集成测试 |
| 私有文件访问控制 | 集成测试 |
| 私有文件临时 URL | 集成测试 |
