# 数据库架构迁移指南

## 架构变更概述

新的数据库架构引入了以下重大变更：

### 1. 新增表
- **services** - 服务表，存储服务信息
- **buckets** - 存储桶表，存储桶信息，与服务表是一对多关系

### 2. uploaded_files 表变更
- **删除字段**：
  - `bucket_name` (string) - 旧的存储桶名称字段
  - `service` (string) - 旧的服务标识字段
  
- **新增字段**：
  - `service_id` (UUID) - 服务ID外键
  - `bucket_id` (UUID) - 存储桶ID外键
  
- **ID字段变更**：
  - 从 `bigint` 改为 `uuid`

### 3. 分表策略变更
- **旧策略**：`uploaded_files_{service}`（例如：`uploaded_files_blog`）
- **新策略**：`uploaded_files_{servicename}_{bucketname}`（例如：`uploaded_files_blog_images`）

## 迁移步骤

### 方案一：全新安装（推荐用于开发/测试环境）

如果数据库中没有重要数据，最简单的方式是删除数据库并重新创建：

```bash
# 1. 删除所有迁移历史
cd backend
dotnet ef database drop --force

# 2. 删除现有迁移文件
# 手动删除 Repositories/Migrations 目录下的所有迁移文件（保留 FileServiceDbContext.cs）

# 3. 创建初始迁移
dotnet ef migrations add InitialMigrationWithServiceAndBucket

# 4. 应用迁移
dotnet ef database update
```

### 方案二：手动迁移（适用于生产环境有数据的情况）

如果数据库中有重要数据需要保留，需要手动迁移：

#### 步骤 1：备份现有数据

```sql
-- 备份 uploaded_files 表
CREATE TABLE uploaded_files_backup AS SELECT * FROM uploaded_files;

-- 备份所有分表（如果有）
-- uploaded_files_blog, uploaded_files_test 等
```

#### 步骤 2：创建新表结构

```sql
-- 创建 services 表
CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    create_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    update_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    is_enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX ix_services_name ON services (name);

-- 创建 buckets 表
CREATE TABLE buckets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    service_id UUID NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    update_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT fk_buckets_services FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ix_buckets_name ON buckets (name);
CREATE INDEX ix_buckets_service_id ON buckets (service_id);
```

#### 步骤 3：数据迁移脚本

```sql
-- 1. 从旧数据提取唯一的服务名，创建服务记录
INSERT INTO services (id, name, is_enabled, create_time, update_time)
SELECT 
    gen_random_uuid(),
    DISTINCT service,
    true,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM uploaded_files_backup
WHERE service IS NOT NULL AND service != '';

-- 如果没有服务名，创建默认服务
INSERT INTO services (id, name, is_enabled, create_time, update_time)
VALUES (
    gen_random_uuid(),
    'default',
    true,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
)
ON CONFLICT (name) DO NOTHING;

-- 2. 从旧数据提取唯一的存储桶名，创建存储桶记录
INSERT INTO buckets (id, name, service_id, is_enabled, create_time, update_time)
SELECT 
    gen_random_uuid(),
    DISTINCT bucket_name,
    (SELECT id FROM services WHERE name = 'default' LIMIT 1),  -- 关联到默认服务
    true,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM uploaded_files_backup
WHERE bucket_name IS NOT NULL AND bucket_name != '';

-- 3. 重新创建 uploaded_files 表（新结构）
DROP TABLE IF EXISTS uploaded_files CASCADE;

CREATE TABLE uploaded_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_hash VARCHAR(64) NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    original_file_name VARCHAR(255),
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_extension VARCHAR(20),
    service_id UUID NOT NULL,
    bucket_id UUID NOT NULL,
    reference_count INTEGER NOT NULL DEFAULT 1,
    uploader_id VARCHAR(450),
    create_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    last_access_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_uploaded_files_services FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT,
    CONSTRAINT fk_uploaded_files_buckets FOREIGN KEY (bucket_id) REFERENCES buckets(id) ON DELETE RESTRICT
);

-- 创建索引
CREATE UNIQUE INDEX ix_uploaded_files_file_hash ON uploaded_files (file_hash) WHERE deleted = false;
CREATE INDEX ix_uploaded_files_service_id_bucket_id ON uploaded_files (service_id, bucket_id);
CREATE INDEX ix_uploaded_files_content_type ON uploaded_files (content_type);
CREATE INDEX ix_uploaded_files_create_time ON uploaded_files (create_time);
CREATE INDEX ix_uploaded_files_deleted ON uploaded_files (deleted);

-- 4. 迁移数据（根据旧的 service 和 bucket_name 查找对应的 ID）
INSERT INTO uploaded_files (
    id, file_hash, file_key, file_url, original_file_name,
    file_size, content_type, file_extension,
    service_id, bucket_id,
    reference_count, uploader_id,
    create_time, last_access_time, deleted
)
SELECT 
    gen_random_uuid(),
    old.file_hash,
    old.file_key,
    old.file_url,
    old.original_file_name,
    old.file_size,
    old.content_type,
    old.file_extension,
    COALESCE(
        (SELECT id FROM services WHERE name = old.service LIMIT 1),
        (SELECT id FROM services WHERE name = 'default' LIMIT 1)
    ) as service_id,
    COALESCE(
        (SELECT id FROM buckets WHERE name = old.bucket_name LIMIT 1),
        (SELECT id FROM buckets WHERE name = 'default' AND service_id = (SELECT id FROM services WHERE name = 'default' LIMIT 1) LIMIT 1)
    ) as bucket_id,
    old.reference_count,
    old.uploader_id,
    old.create_time,
    old.last_access_time,
    old.deleted
FROM uploaded_files_backup old;
```

#### 步骤 4：更新 EF Core 迁移历史

```sql
-- 删除旧的迁移记录
DELETE FROM "__EFMigrationsHistory";

-- 手动标记迁移为已应用
INSERT INTO "__EFMigrationsHistory" (migration_id, product_version)
VALUES ('20251111104034_AddServiceAndBucketTables', '9.0.0');
```

## 验证迁移

迁移完成后，验证以下内容：

```sql
-- 1. 检查表是否正确创建
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('services', 'buckets', 'uploaded_files');

-- 2. 检查数据量
SELECT 
    (SELECT COUNT(*) FROM services) as services_count,
    (SELECT COUNT(*) FROM buckets) as buckets_count,
    (SELECT COUNT(*) FROM uploaded_files) as files_count;

-- 3. 检查外键关系
SELECT
    conname as constraint_name,
    conrelid::regclass as table_name,
    confrelid::regclass as referenced_table
FROM pg_constraint
WHERE contype = 'f'
AND conrelid::regclass::text IN ('uploaded_files', 'buckets');
```

## 新架构说明

### 服务（Service）
- 代表一个业务服务（如：blog、market、admin）
- 一个服务可以有多个存储桶
- 服务名全局唯一

### 存储桶（Bucket）
- 存储桶属于某个服务
- 用于在同一服务下区分不同类型的文件
- 存储桶名全局唯一（不限于同一服务内）

### 分表策略
- 分表名格式：`uploaded_files_{servicename}_{bucketname}`
- 例如：blog 服务的 images 存储桶 → `uploaded_files_blog_images`
- 分表会自动创建，无需手动维护

## 回滚

如果迁移出现问题，可以使用备份数据回滚：

```sql
-- 1. 删除新表
DROP TABLE IF EXISTS uploaded_files CASCADE;
DROP TABLE IF EXISTS buckets CASCADE;
DROP TABLE IF EXISTS services CASCADE;

-- 2. 恢复备份
CREATE TABLE uploaded_files AS SELECT * FROM uploaded_files_backup;

-- 3. 重建索引（根据旧结构）
CREATE UNIQUE INDEX ix_uploaded_files_file_hash ON uploaded_files (file_hash) WHERE deleted = false;
CREATE INDEX ix_uploaded_files_service ON uploaded_files (service);
-- 其他索引...
```

## 注意事项

1. **备份重要！** 在执行任何迁移前，务必备份数据库
2. **测试环境先行**：先在测试环境验证迁移脚本
3. **停机时间**：生产环境迁移可能需要停机维护
4. **分表迁移**：如果使用了分表（`uploaded_files_*`），需要对每个分表执行相同的迁移
5. **API兼容性**：迁移后，旧的基于字符串 service/bucket_name 的API已更新为使用ID

## 问题排查

### 问题1：迁移失败 - ID类型转换错误
**错误**：`column "id" cannot be cast automatically to type uuid`

**解决**：采用方案一（全新安装）或方案二（手动迁移）

### 问题2：外键约束错误
**错误**：`violates foreign key constraint`

**解决**：确保 services 和 buckets 表中存在对应的记录

### 问题3：数据丢失
**原因**：未正确执行数据迁移步骤

**解决**：从备份表恢复数据
