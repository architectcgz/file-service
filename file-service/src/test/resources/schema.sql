-- Upload Service Test Database Schema (H2 Compatible)

-- Storage Objects Table
CREATE TABLE IF NOT EXISTS storage_objects (
    id              VARCHAR(36) PRIMARY KEY,
    app_id          VARCHAR(64) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL,
    hash_algorithm  VARCHAR(16) NOT NULL DEFAULT 'MD5',
    storage_path    VARCHAR(512) NOT NULL,
    file_size       BIGINT NOT NULL,
    content_type    VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    reference_count INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_storage_objects_app_hash ON storage_objects(app_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_storage_objects_reference_count ON storage_objects(reference_count);
CREATE INDEX IF NOT EXISTS idx_storage_objects_created_at ON storage_objects(created_at);

-- File Records Table
CREATE TABLE IF NOT EXISTS file_records (
    id                VARCHAR(36) PRIMARY KEY,
    app_id            VARCHAR(64) NOT NULL,
    user_id           BIGINT NOT NULL,
    storage_object_id VARCHAR(36) NOT NULL,
    original_name     VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(512),
    file_size         BIGINT NOT NULL,
    content_type      VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    file_hash         VARCHAR(64) NOT NULL,
    access_level      VARCHAR(16) NOT NULL DEFAULT 'public',
    status            VARCHAR(32) NOT NULL DEFAULT 'completed',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_records_app_id ON file_records(app_id);
CREATE INDEX IF NOT EXISTS idx_file_records_user_id ON file_records(user_id);
CREATE INDEX IF NOT EXISTS idx_file_records_storage_object_id ON file_records(storage_object_id);
CREATE INDEX IF NOT EXISTS idx_file_records_file_hash ON file_records(file_hash);
CREATE INDEX IF NOT EXISTS idx_file_records_user_hash ON file_records(user_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_file_records_access_level ON file_records(access_level);
CREATE INDEX IF NOT EXISTS idx_file_records_status ON file_records(status);
CREATE INDEX IF NOT EXISTS idx_file_records_created_at ON file_records(created_at);

-- Upload Tasks Table
CREATE TABLE IF NOT EXISTS upload_tasks (
    id              VARCHAR(36) PRIMARY KEY,
    app_id          VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_hash       VARCHAR(64),
    content_type    VARCHAR(128),
    storage_path    VARCHAR(512) NOT NULL,
    upload_id       VARCHAR(256) NOT NULL,
    total_parts     INT NOT NULL,
    chunk_size      INT NOT NULL DEFAULT 5242880,
    status          VARCHAR(32) NOT NULL DEFAULT 'uploading',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_upload_tasks_app_id ON upload_tasks(app_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_user_id ON upload_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_file_hash ON upload_tasks(file_hash);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_upload_id ON upload_tasks(upload_id);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_status ON upload_tasks(status);
CREATE INDEX IF NOT EXISTS idx_upload_tasks_expires_at ON upload_tasks(expires_at);

-- Upload Parts Table
CREATE TABLE IF NOT EXISTS upload_parts (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    part_number     INT NOT NULL,
    etag            VARCHAR(64) NOT NULL,
    size            BIGINT NOT NULL,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_parts_task_part ON upload_parts(task_id, part_number);
CREATE INDEX IF NOT EXISTS idx_upload_parts_task_id ON upload_parts(task_id);

-- Tenant Usage Table
CREATE TABLE IF NOT EXISTS tenant_usage (
    tenant_id           VARCHAR(32) PRIMARY KEY,
    used_storage_bytes  BIGINT NOT NULL DEFAULT 0,
    used_file_count     INT NOT NULL DEFAULT 0,
    last_upload_at      TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_usage_updated_at ON tenant_usage(updated_at);
