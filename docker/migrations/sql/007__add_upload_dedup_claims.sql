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

CREATE INDEX IF NOT EXISTS idx_upload_dedup_claims_expires_at
    ON upload_dedup_claims(expires_at);

COMMENT ON TABLE upload_dedup_claims IS '上传去重占位表 - 用于缩小同 hash 上传串行区';
COMMENT ON COLUMN upload_dedup_claims.bucket_name IS '标准化后的存储桶名称，避免 NULL 参与唯一键';
COMMENT ON COLUMN upload_dedup_claims.owner_token IS '当前持有上传资格的请求令牌';
COMMENT ON COLUMN upload_dedup_claims.expires_at IS 'claim 过期时间，避免异常退出后永久占位';
