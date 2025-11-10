-- ============================================
-- File Service 数据库初始化脚本
-- ============================================
-- 说明：在部署前运行此脚本创建数据库和用户

-- 创建数据库
CREATE DATABASE file_service
    WITH 
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TEMPLATE = template0;

-- 连接到新创建的数据库
\c file_service

-- 创建扩展（如果需要）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 设置时区
ALTER DATABASE file_service SET timezone TO 'UTC';

-- 授予权限（如果使用独立用户）
-- GRANT ALL PRIVILEGES ON DATABASE file_service TO your_user;

-- 显示成功信息
SELECT 'Database file_service created successfully!' AS status;

