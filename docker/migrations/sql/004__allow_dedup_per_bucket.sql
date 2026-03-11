DROP INDEX IF EXISTS idx_storage_objects_app_hash;

CREATE UNIQUE INDEX IF NOT EXISTS idx_storage_objects_app_hash_bucket
    ON storage_objects(app_id, file_hash, hash_algorithm, bucket_name);
