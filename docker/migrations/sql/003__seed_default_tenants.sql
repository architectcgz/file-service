INSERT INTO tenants (
    tenant_id,
    tenant_name,
    status,
    max_storage_bytes,
    max_file_count,
    max_single_file_size,
    contact_email,
    created_at,
    updated_at
)
VALUES
    ('blog', 'Blog Application', 'active', 10737418240, 10000, 104857600, 'admin@blog.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('im', 'IM Application', 'active', 21474836480, 50000, 104857600, 'admin@im.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO tenant_usage (
    tenant_id,
    used_storage_bytes,
    used_file_count,
    updated_at
)
VALUES
    ('blog', 0, 0, CURRENT_TIMESTAMP),
    ('im', 0, 0, CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;
