ALTER TABLE storage_objects
    ADD COLUMN IF NOT EXISTS bucket_name VARCHAR(128);

UPDATE storage_objects
SET bucket_name = :'default_bucket'
WHERE bucket_name IS NULL;

COMMENT ON COLUMN storage_objects.bucket_name IS '对象所在存储桶名称，用于多桶部署下的精确定位';
