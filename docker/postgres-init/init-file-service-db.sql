-- =====================================================
-- File Service Database Initialization
-- =====================================================

-- 创建 file_service 数据库
CREATE DATABASE file_service;

-- 连接到 file_service 数据库
\c file_service;

-- 注意：表结构将通过 Flyway 自动创建
-- 此脚本仅负责创建数据库本身

-- =====================================================
-- Default Tenant Initialization
-- =====================================================
-- This section creates default tenants for common applications
-- These INSERT statements will be executed AFTER Flyway migrations
-- due to the execution order in docker-compose.yml

-- Insert default tenants (if they don't exist)
INSERT INTO tenants (tenant_id, tenant_name, status, max_storage_bytes, max_file_count, max_single_file_size, contact_email, created_at, updated_at)
VALUES 
    ('blog', 'Blog Application', 'ACTIVE', 10737418240, 10000, 104857600, 'admin@blog.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('im', 'IM Application', 'ACTIVE', 21474836480, 50000, 104857600, 'admin@im.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert corresponding tenant usage records (if they don't exist)
INSERT INTO tenant_usage (tenant_id, used_storage_bytes, used_file_count, updated_at)
VALUES 
    ('blog', 0, 0, CURRENT_TIMESTAMP),
    ('im', 0, 0, CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

-- Verify the inserted data
SELECT 
    t.tenant_id,
    t.tenant_name,
    t.status,
    t.max_storage_bytes / 1024 / 1024 / 1024 AS max_storage_gb,
    t.max_file_count,
    t.max_single_file_size / 1024 / 1024 AS max_single_file_mb,
    tu.used_storage_bytes,
    tu.used_file_count
FROM tenants t
LEFT JOIN tenant_usage tu ON t.tenant_id = tu.tenant_id
WHERE t.tenant_id IN ('blog', 'im');

-- Output summary
DO $$
BEGIN
    RAISE NOTICE '✓ Default tenants initialized successfully!';
    RAISE NOTICE '  - blog: 10GB storage, 10000 files max';
    RAISE NOTICE '  - im: 20GB storage, 50000 files max';
END $$;
