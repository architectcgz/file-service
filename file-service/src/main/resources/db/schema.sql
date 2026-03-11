-- =====================================================
-- File Service Database Schema - 完整版本
-- 文件服务数据库表结构（支持多租户）
-- 最后更新: 2026-02-11
-- =====================================================

-- 文件记录表
CREATE TABLE IF NOT EXISTS file_records (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    storage_object_id VARCHAR(64),
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    file_hash VARCHAR(128),
    hash_algorithm VARCHAR(20) DEFAULT 'MD5',
    access_level VARCHAR(20) DEFAULT 'public',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- file_records 表索引
CREATE INDEX IF NOT EXISTS idx_file_records_app_user ON file_records(app_id, user_id);
CREATE INDEX IF NOT EXISTS idx_file_records_app_status ON file_records(app_id, status);
CREATE INDEX IF NOT EXISTS idx_file_records_storage_object ON file_records(storage_object_id);
CREATE INDEX IF NOT EXISTS idx_file_records_created_at ON file_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_file_records_tenant ON file_records(app_id);

-- 存储对象表（用于文件去重）
CREATE TABLE IF NOT EXISTS storage_objects (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    file_hash VARCHAR(128) NOT NULL,
    hash_algorithm VARCHAR(20) NOT NULL DEFAULT 'MD5',
    storage_path VARCHAR(500) NOT NULL,
    bucket_name VARCHAR(128),
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    reference_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- storage_objects 表唯一索引（app_id + file_hash + hash_algorithm）
CREATE UNIQUE INDEX IF NOT EXISTS idx_storage_objects_app_hash 
    ON storage_objects(app_id, file_hash, hash_algorithm);
CREATE INDEX IF NOT EXISTS idx_storage_objects_ref_count ON storage_objects(reference_count);

-- 上传任务表（分片上传）
CREATE TABLE IF NOT EXISTS upload_tasks (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    chunk_size BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    uploaded_chunks INT NOT NULL DEFAULT 0,
    upload_id VARCHAR(255),
    storage_path VARCHAR(500),
    file_hash VARCHAR(128),
    hash_algorithm VARCHAR(20) DEFAULT 'MD5',
    status VARCHAR(20) NOT NULL DEFAULT 'uploading',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- upload_tasks 表索引
CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_user ON upload_tasks(app_id, user_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_status ON upload_tasks(app_id, status);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_expires_at ON upload_tasks(expires_at) 
    WHERE expires_at IS NOT NULL;

-- 上传分片表
CREATE TABLE IF NOT EXISTS upload_parts (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    part_number INT NOT NULL,
    etag VARCHAR(255),
    size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- upload_parts 表索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_upload_parts_unique ON upload_parts(task_id, part_number);
CREATE INDEX IF NOT EXISTS idx_upload_parts_task_id ON upload_parts(task_id);
CREATE INDEX IF NOT EXISTS idx_upload_parts_task_part ON upload_parts(task_id, part_number);
CREATE INDEX IF NOT EXISTS idx_upload_parts_task_uploaded ON upload_parts(task_id, uploaded_at DESC);

-- 租户表
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(32) PRIMARY KEY,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    max_storage_bytes BIGINT NOT NULL DEFAULT 10737418240,
    max_file_count INTEGER NOT NULL DEFAULT 10000,
    max_single_file_size BIGINT NOT NULL DEFAULT 104857600,
    allowed_file_types TEXT[],
    contact_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenant_status CHECK (status IN ('active', 'suspended', 'deleted'))
);

-- 租户表索引
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_created_at ON tenants(created_at);

-- 租户使用统计表
CREATE TABLE IF NOT EXISTS tenant_usage (
    tenant_id VARCHAR(32) PRIMARY KEY,
    used_storage_bytes BIGINT NOT NULL DEFAULT 0,
    used_file_count INTEGER NOT NULL DEFAULT 0,
    last_upload_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_used_storage_bytes CHECK (used_storage_bytes >= 0),
    CONSTRAINT chk_used_file_count CHECK (used_file_count >= 0)
);

-- tenant_usage 表索引
CREATE INDEX IF NOT EXISTS idx_tenant_usage_updated_at ON tenant_usage(updated_at);

-- 管理员审计日志表
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    admin_user_id VARCHAR(64) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(64),
    tenant_id VARCHAR(32),
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- admin_audit_logs 表索引
CREATE INDEX IF NOT EXISTS idx_audit_admin ON admin_audit_logs(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_time ON admin_audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_tenant ON admin_audit_logs(tenant_id);

-- =====================================================
-- 表注释
-- =====================================================

COMMENT ON TABLE file_records IS '文件记录表 - 存储用户上传的文件元数据';
COMMENT ON TABLE storage_objects IS '存储对象表 - 用于文件去重，多个文件记录可共享同一存储对象';
COMMENT ON TABLE upload_tasks IS '上传任务表 - 用于分片上传场景';
COMMENT ON TABLE upload_parts IS '上传分片表 - 记录分片上传的每个分片信息';
COMMENT ON TABLE tenants IS '租户表 - 存储租户信息和配额配置';

-- =====================================================
-- 索引注释 - upload_parts
-- =====================================================

COMMENT ON INDEX idx_upload_parts_task_id IS '用于查询任务的所有分片';
COMMENT ON INDEX idx_upload_parts_task_part IS '用于快速检查特定分片是否已上传';
COMMENT ON INDEX idx_upload_parts_task_uploaded IS '用于按时间排序查询分片';

-- =====================================================
-- 表注释（续）
-- =====================================================

COMMENT ON TABLE tenants IS '租户表 - 存储租户信息和配额配置';
COMMENT ON TABLE tenant_usage IS '租户使用统计表 - 记录租户的存储使用情况';
COMMENT ON TABLE admin_audit_logs IS '管理员审计日志表 - 记录所有管理操作';

-- =====================================================
-- 字段注释 - file_records
-- =====================================================

COMMENT ON COLUMN file_records.app_id IS '应用标识符，用于多租户隔离（如：blog, im）';
COMMENT ON COLUMN file_records.user_id IS '用户ID - 支持字符串类型的用户标识';
COMMENT ON COLUMN file_records.status IS '文件状态: pending=待处理, completed=已完成, deleted=已删除';
COMMENT ON COLUMN file_records.access_level IS '访问级别: public=公开, private=私有';

-- =====================================================
-- 字段注释 - storage_objects
-- =====================================================

COMMENT ON COLUMN storage_objects.app_id IS '应用标识符，用于多租户隔离（如：blog, im）';
COMMENT ON COLUMN storage_objects.bucket_name IS '对象所在存储桶名称，用于多桶部署下的精确定位';
COMMENT ON COLUMN storage_objects.reference_count IS '引用计数 - 记录有多少文件记录引用此存储对象';

-- =====================================================
-- 字段注释 - upload_tasks
-- =====================================================

COMMENT ON COLUMN upload_tasks.app_id IS '应用标识符，用于多租户隔离（如：blog, im）';
COMMENT ON COLUMN upload_tasks.user_id IS '用户ID - 支持字符串类型的用户标识';
COMMENT ON COLUMN upload_tasks.status IS '上传状态: uploading=上传中, completed=已完成, aborted=已中止, expired=已过期';

-- =====================================================
-- 字段注释 - tenants
-- =====================================================

COMMENT ON COLUMN tenants.tenant_id IS '租户ID - 唯一标识符';
COMMENT ON COLUMN tenants.tenant_name IS '租户名称';
COMMENT ON COLUMN tenants.status IS '租户状态: active=活跃, suspended=停用, deleted=已删除';
COMMENT ON COLUMN tenants.max_storage_bytes IS '最大存储空间（字节），默认 10GB';
COMMENT ON COLUMN tenants.max_file_count IS '最大文件数量，默认 10000';
COMMENT ON COLUMN tenants.max_single_file_size IS '单文件大小限制（字节），默认 100MB';
COMMENT ON COLUMN tenants.allowed_file_types IS '允许的文件类型列表';
COMMENT ON COLUMN tenants.contact_email IS '联系邮箱';

-- =====================================================
-- 字段注释 - tenant_usage
-- =====================================================

COMMENT ON COLUMN tenant_usage.tenant_id IS '租户ID - 对应 tenants 表的 tenant_id';
COMMENT ON COLUMN tenant_usage.used_storage_bytes IS '已使用存储空间（字节）';
COMMENT ON COLUMN tenant_usage.used_file_count IS '已使用文件数量';
COMMENT ON COLUMN tenant_usage.last_upload_at IS '最后上传时间';

-- =====================================================
-- 字段注释 - admin_audit_logs
-- =====================================================

COMMENT ON COLUMN admin_audit_logs.id IS '审计日志唯一标识符';
COMMENT ON COLUMN admin_audit_logs.admin_user_id IS '执行操作的管理员ID';
COMMENT ON COLUMN admin_audit_logs.action IS '操作类型（如：DELETE_FILE, CREATE_TENANT）';
COMMENT ON COLUMN admin_audit_logs.target_type IS '目标实体类型（如：FILE, TENANT）';
COMMENT ON COLUMN admin_audit_logs.target_id IS '目标实体ID';
COMMENT ON COLUMN admin_audit_logs.tenant_id IS '关联的租户ID（如适用）';
COMMENT ON COLUMN admin_audit_logs.details IS 'JSON 格式的操作详细信息';
COMMENT ON COLUMN admin_audit_logs.ip_address IS '管理员IP地址';
COMMENT ON COLUMN admin_audit_logs.created_at IS '操作执行时间';
