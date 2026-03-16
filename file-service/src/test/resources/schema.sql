-- Upload Service Test Database Schema (H2 Compatible)

-- Storage Objects Table
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

CREATE UNIQUE INDEX IF NOT EXISTS idx_storage_objects_app_hash_bucket
    ON storage_objects(app_id, file_hash, hash_algorithm, bucket_name);
CREATE INDEX IF NOT EXISTS idx_storage_objects_ref_count ON storage_objects(reference_count);

-- Upload Dedup Claims Table
CREATE TABLE IF NOT EXISTS upload_dedup_claims (
    app_id       VARCHAR(64) NOT NULL,
    file_hash    VARCHAR(64) NOT NULL,
    bucket_name  VARCHAR(128) NOT NULL,
    owner_token  VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, file_hash, bucket_name)
);

CREATE INDEX IF NOT EXISTS idx_upload_dedup_claims_expires_at ON upload_dedup_claims(expires_at);

-- File Records Table
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

CREATE INDEX IF NOT EXISTS idx_file_records_app_user ON file_records(app_id, user_id);
CREATE INDEX IF NOT EXISTS idx_file_records_app_status ON file_records(app_id, status);
CREATE INDEX IF NOT EXISTS idx_file_records_storage_object ON file_records(storage_object_id);
CREATE INDEX IF NOT EXISTS idx_file_records_created_at ON file_records(created_at);
CREATE INDEX IF NOT EXISTS idx_file_records_tenant ON file_records(app_id);

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

CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_user ON upload_tasks(app_id, user_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_status ON upload_tasks(app_id, status);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_expires_at ON upload_tasks(expires_at)
    WHERE expires_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_tasks_active_hash
    ON upload_tasks(app_id, user_id, file_hash)
    WHERE file_hash IS NOT NULL
      AND status IN ('initiated', 'uploading', 'completing');

-- Tenants Table
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id            VARCHAR(32) PRIMARY KEY,
    tenant_name          VARCHAR(128) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'active',
    max_storage_bytes    BIGINT NOT NULL DEFAULT 10737418240,
    max_file_count       INT NOT NULL DEFAULT 10000,
    max_single_file_size BIGINT NOT NULL DEFAULT 104857600,
    allowed_file_types   TEXT ARRAY,
    contact_email        VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_created_at ON tenants(created_at);

-- Tenant Usage Table
CREATE TABLE IF NOT EXISTS tenant_usage (
    tenant_id           VARCHAR(32) PRIMARY KEY,
    used_storage_bytes  BIGINT NOT NULL DEFAULT 0,
    used_file_count     INT NOT NULL DEFAULT 0,
    last_upload_at      TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_usage_updated_at ON tenant_usage(updated_at);

-- Admin Audit Logs Table
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id             VARCHAR(36) PRIMARY KEY,
    admin_user_id  VARCHAR(64) NOT NULL,
    action         VARCHAR(50) NOT NULL,
    target_type    VARCHAR(50) NOT NULL,
    target_id      VARCHAR(64),
    tenant_id      VARCHAR(32),
    details        JSONB,
    ip_address     VARCHAR(45),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_admin ON admin_audit_logs(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_time ON admin_audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_tenant ON admin_audit_logs(tenant_id);
