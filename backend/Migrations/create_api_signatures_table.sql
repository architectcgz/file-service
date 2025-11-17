-- 创建 API 签名表
CREATE TABLE IF NOT EXISTS api_signatures (
    id BIGSERIAL PRIMARY KEY,
    signature_token VARCHAR(128) NOT NULL,
    caller_service VARCHAR(100) NOT NULL,
    caller_service_id VARCHAR(100),
    allowed_operation VARCHAR(50) NOT NULL,
    allowed_file_types VARCHAR(200),
    max_file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    max_usage_count INTEGER NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(200),
    creator_ip VARCHAR(50)
);

-- 创建索引
CREATE UNIQUE INDEX IF NOT EXISTS IX_ApiSignatures_SignatureToken_Unique 
    ON api_signatures(signature_token);

CREATE INDEX IF NOT EXISTS IX_ApiSignatures_Status_ExpiresAt 
    ON api_signatures(status, expires_at);

CREATE INDEX IF NOT EXISTS IX_ApiSignatures_CallerService 
    ON api_signatures(caller_service);

CREATE INDEX IF NOT EXISTS IX_ApiSignatures_CreatedAt 
    ON api_signatures(created_at DESC);

-- 添加注释
COMMENT ON TABLE api_signatures IS 'API签名表 - 用于服务间调用的临时签名管理';
COMMENT ON COLUMN api_signatures.signature_token IS '签名Token（唯一）';
COMMENT ON COLUMN api_signatures.caller_service IS '调用方服务名称';
COMMENT ON COLUMN api_signatures.allowed_operation IS '允许的操作类型';
COMMENT ON COLUMN api_signatures.status IS '签名状态：active, expired, revoked';
COMMENT ON COLUMN api_signatures.expires_at IS '过期时间';
COMMENT ON COLUMN api_signatures.usage_count IS '已使用次数';
COMMENT ON COLUMN api_signatures.max_usage_count IS '最大使用次数（0表示无限制）';
