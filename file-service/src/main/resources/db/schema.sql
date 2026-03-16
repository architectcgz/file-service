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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- storage_objects 表唯一索引（app_id + file_hash + hash_algorithm + bucket_name）
CREATE UNIQUE INDEX IF NOT EXISTS idx_storage_objects_app_hash_bucket
    ON storage_objects(app_id, file_hash, hash_algorithm, bucket_name);
CREATE INDEX IF NOT EXISTS idx_storage_objects_ref_count ON storage_objects(reference_count);

-- 上传去重占位表（缩小同 hash 上传串行区）
CREATE TABLE IF NOT EXISTS upload_dedup_claims (
    app_id VARCHAR(32) NOT NULL,
    file_hash VARCHAR(128) NOT NULL,
    bucket_name VARCHAR(128) NOT NULL,
    owner_token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, file_hash, bucket_name)
);

CREATE INDEX IF NOT EXISTS idx_upload_dedup_claims_expires_at ON upload_dedup_claims(expires_at);

-- 上传会话表（file-core upload session）
CREATE TABLE IF NOT EXISTS upload_tasks (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    upload_mode VARCHAR(32),
    access_level VARCHAR(20) DEFAULT 'public',
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    chunk_size BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    uploaded_chunks INT NOT NULL DEFAULT 0,
    upload_id VARCHAR(255),
    storage_path VARCHAR(500),
    file_id VARCHAR(64),
    file_hash VARCHAR(128),
    hash_algorithm VARCHAR(20) DEFAULT 'MD5',
    status VARCHAR(20) NOT NULL DEFAULT 'uploading',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

-- upload_tasks 表索引
CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_user ON upload_tasks(app_id, user_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_status ON upload_tasks(app_id, status);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_expires_at ON upload_tasks(expires_at) 
    WHERE expires_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_tasks_active_hash
    ON upload_tasks(app_id, user_id, file_hash)
    WHERE file_hash IS NOT NULL
      AND status IN ('initiated', 'uploading', 'completing');

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    last_upload_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
COMMENT ON TABLE upload_dedup_claims IS '上传去重占位表 - 用于缩小同 hash 上传串行区';
COMMENT ON TABLE upload_tasks IS '上传会话表 - file-core upload session 的持久化视图';
COMMENT ON TABLE tenants IS '租户表 - 存储租户信息和配额配置';

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
COMMENT ON COLUMN upload_dedup_claims.bucket_name IS '标准化后的存储桶名称，避免 NULL 参与唯一键';
COMMENT ON COLUMN upload_dedup_claims.owner_token IS '当前持有上传资格的请求令牌';
COMMENT ON COLUMN upload_dedup_claims.expires_at IS 'claim 过期时间，避免异常退出后永久占位';

-- =====================================================
-- 字段注释 - upload_tasks
-- =====================================================

COMMENT ON COLUMN upload_tasks.app_id IS '应用标识符，用于多租户隔离（如：blog, im）';
COMMENT ON COLUMN upload_tasks.user_id IS '用户ID - 支持字符串类型的用户标识';
COMMENT ON COLUMN upload_tasks.upload_mode IS 'V2 上传模式: inline, direct, presigned_single';
COMMENT ON COLUMN upload_tasks.access_level IS 'V2 目标访问级别: public, private';
COMMENT ON COLUMN upload_tasks.file_id IS 'V2 上传会话关联的最终文件ID';
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
