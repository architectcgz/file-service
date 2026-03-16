WITH ranked_active_sessions AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY app_id, user_id, file_hash
               ORDER BY updated_at DESC, created_at DESC, id DESC
           ) AS row_num
    FROM upload_tasks
    WHERE file_hash IS NOT NULL
      AND status IN ('initiated', 'uploading', 'completing')
)
UPDATE upload_tasks AS upload_task
SET status = 'expired',
    updated_at = CURRENT_TIMESTAMP,
    expires_at = COALESCE(upload_task.expires_at, CURRENT_TIMESTAMP)
FROM ranked_active_sessions AS ranked
WHERE upload_task.id = ranked.id
  AND ranked.row_num > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_tasks_active_hash
    ON upload_tasks(app_id, user_id, file_hash)
    WHERE file_hash IS NOT NULL
      AND status IN ('initiated', 'uploading', 'completing');
