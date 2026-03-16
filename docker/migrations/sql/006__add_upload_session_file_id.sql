ALTER TABLE upload_tasks
    ADD COLUMN IF NOT EXISTS file_id VARCHAR(64);

COMMENT ON COLUMN upload_tasks.file_id IS 'V2 上传会话关联的最终文件ID';
