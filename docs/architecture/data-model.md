# 数据模型

## 领域模型

### FileRecord — 文件记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 主键（UUIDv7） |
| appId | String | 租户/应用标识 |
| userId | String | 上传用户 |
| storageObjectId | String | 关联的存储对象 |
| originalFilename | String | 原始文件名 |
| storagePath | String | 存储路径 |
| fileSize | Long | 文件大小（字节） |
| contentType | String | MIME 类型 |
| fileHash | String | 文件哈希值 |
| hashAlgorithm | String | 哈希算法 |
| status | FileStatus | 文件状态 |
| accessLevel | AccessLevel | 访问级别 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

关键方法：`belongsToApp()`, `isDeleted()`, `isCompleted()`, `markAsDeleted()`

### StorageObject — 存储对象（去重核心）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 主键（UUIDv7） |
| appId | String | 租户标识 |
| fileHash | String | 文件哈希 |
| hashAlgorithm | String | 哈希算法 |
| storagePath | String | S3 存储路径 |
| fileSize | Long | 文件大小 |
| contentType | String | MIME 类型 |
| referenceCount | Integer | 引用计数 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

关键方法：`incrementReferenceCount()`, `decrementReferenceCount()`, `canBeDeleted()`

### UploadTask — 上传任务

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 主键（UUIDv7） |
| appId | String | 租户标识 |
| userId | String | 用户标识 |
| fileName | String | 文件名 |
| fileSize | Long | 文件大小 |
| fileHash | String | 文件哈希 |
| storagePath | String | 存储路径 |
| uploadId | String | S3 分片上传 ID |
| totalParts | Integer | 总分片数 |
| chunkSize | Long | 分片大小 |
| status | UploadTaskStatus | 任务状态 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| expiresAt | LocalDateTime | 过期时间 |

关键方法：`isExpired()`, `canResume()`, `markCompleted()`, `markAborted()`, `markExpired()`

### UploadPart — 上传分片

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 主键 |
| taskId | String | 关联任务 ID |
| partNumber | Integer | 分片序号 |
| etag | String | S3 返回的 ETag |
| size | Long | 分片大小 |
| uploadedAt | LocalDateTime | 上传时间 |

### Tenant — 租户

| 字段 | 类型 | 说明 |
|------|------|------|
| tenantId | String | 租户 ID |
| tenantName | String | 租户名称 |
| status | TenantStatus | 租户状态 |
| maxStorageBytes | Long | 最大存储空间 |
| maxFileCount | Long | 最大文件数 |
| maxSingleFileSize | Long | 单文件大小上限 |
| allowedFileTypes | String | 允许的文件类型 |
| contactEmail | String | 联系邮箱 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

## 状态机

### FileStatus — 文件状态

```
PENDING ──→ COMPLETED ──→ DELETED（软删除）
```

### UploadTaskStatus — 上传任务状态

```
UPLOADING ──┬──→ COMPLETED（正常完成）
            ├──→ ABORTED（用户中止）
            └──→ EXPIRED（超时过期，定时任务清理）
```

### TenantStatus — 租户状态

```
ACTIVE ⇄ SUSPENDED ──→ DELETED（软删除）
```

### AccessLevel — 访问级别

```
PUBLIC  — 永久 CDN URL，缓存到 Redis
PRIVATE — 临时预签名 URL（有效期 3600s），不缓存
```

## 数据库表结构

### file_records

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(64) | PK | 主键 |
| app_id | VARCHAR(32) | NOT NULL | 租户标识 |
| user_id | VARCHAR(64) | NOT NULL | 用户标识 |
| storage_object_id | VARCHAR(64) | NOT NULL | 存储对象 ID |
| original_filename | VARCHAR(255) | | 原始文件名 |
| storage_path | VARCHAR(512) | | 存储路径 |
| file_size | BIGINT | | 文件大小 |
| content_type | VARCHAR(128) | | MIME 类型 |
| file_hash | VARCHAR(128) | | 文件哈希 |
| hash_algorithm | VARCHAR(16) | | 哈希算法 |
| status | VARCHAR(16) | | 文件状态 |
| access_level | VARCHAR(16) | | 访问级别 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

索引：`(app_id, user_id)`, `(app_id, status)`, `storage_object_id`, `created_at DESC`, `app_id`

### storage_objects

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(64) | PK | 主键 |
| app_id | VARCHAR(32) | NOT NULL | 租户标识 |
| file_hash | VARCHAR(128) | NOT NULL | 文件哈希 |
| hash_algorithm | VARCHAR(16) | NOT NULL | 哈希算法 |
| storage_path | VARCHAR(512) | | 存储路径 |
| file_size | BIGINT | | 文件大小 |
| content_type | VARCHAR(128) | | MIME 类型 |
| reference_count | INTEGER | DEFAULT 0 | 引用计数 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

唯一索引：`(app_id, file_hash, hash_algorithm)`

### upload_tasks

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(64) | PK | 主键 |
| app_id | VARCHAR(32) | NOT NULL | 租户标识 |
| user_id | VARCHAR(64) | NOT NULL | 用户标识 |
| file_name | VARCHAR(255) | | 文件名 |
| file_size | BIGINT | | 文件大小 |
| file_hash | VARCHAR(128) | | 文件哈希 |
| storage_path | VARCHAR(512) | | 存储路径 |
| upload_id | VARCHAR(256) | | S3 上传 ID |
| total_parts | INTEGER | | 总分片数 |
| chunk_size | BIGINT | | 分片大小 |
| status | VARCHAR(16) | | 任务状态 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |
| expires_at | TIMESTAMP | | 过期时间 |

索引：`(app_id, user_id)`, `(app_id, status)`, `expires_at`

### upload_parts

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(64) | PK | 主键 |
| task_id | VARCHAR(64) | NOT NULL | 任务 ID |
| part_number | INTEGER | NOT NULL | 分片序号 |
| etag | VARCHAR(128) | | S3 ETag |
| size | BIGINT | | 分片大小 |
| uploaded_at | TIMESTAMP | | 上传时间 |

唯一索引：`(task_id, part_number)`

### tenants

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| tenant_id | VARCHAR(32) | PK | 租户 ID |
| tenant_name | VARCHAR(128) | | 租户名称 |
| status | VARCHAR(16) | | 租户状态 |
| max_storage_bytes | BIGINT | | 最大存储空间 |
| max_file_count | BIGINT | | 最大文件数 |
| max_single_file_size | BIGINT | | 单文件大小上限 |
| allowed_file_types | TEXT | | 允许的文件类型 |
| contact_email | VARCHAR(128) | | 联系邮箱 |
| created_at | TIMESTAMP | | 创建时间 |
| updated_at | TIMESTAMP | | 更新时间 |

### tenant_usage

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| tenant_id | VARCHAR(32) | PK | 租户 ID |
| used_storage_bytes | BIGINT | | 已用存储 |
| file_count | BIGINT | | 文件数量 |
| updated_at | TIMESTAMP | | 更新时间 |

### admin_audit_logs

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | 主键 |
| admin_user_id | VARCHAR(64) | | 操作人 |
| action | VARCHAR(32) | | 操作类型 |
| tenant_id | VARCHAR(32) | | 租户 ID |
| created_at | TIMESTAMP | | 操作时间 |

索引：`admin_user_id`, `action`, `created_at`, `tenant_id`

## 实体关系

```
Tenant 1──N FileRecord
  │
  └── 1──1 TenantUsage

StorageObject 1──N FileRecord（通过引用计数管理）

UploadTask 1──N UploadPart
  │
  └── 完成后 → 创建 StorageObject + FileRecord
```
