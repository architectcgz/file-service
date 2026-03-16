ALTER TABLE upload_tasks
    ADD COLUMN IF NOT EXISTS upload_mode VARCHAR(32);

ALTER TABLE upload_tasks
    ADD COLUMN IF NOT EXISTS access_level VARCHAR(20);

UPDATE upload_tasks
SET upload_mode = 'direct'
WHERE upload_mode IS NULL;

UPDATE upload_tasks
SET access_level = 'public'
WHERE access_level IS NULL;

COMMENT ON COLUMN upload_tasks.upload_mode IS 'V2 上传模式: inline, direct, presigned_single';
COMMENT ON COLUMN upload_tasks.access_level IS 'V2 目标访问级别: public, private';
