-- =====================================================
-- File Service Database Initialization
-- =====================================================

-- 创建 file_service 数据库
CREATE DATABASE file_service;

-- 连接到 file_service 数据库
\c file_service;

-- 表结构和种子数据由独立 migration 容器执行。
-- 此脚本只负责创建数据库，避免在 schema 尚未创建时执行种子 SQL。
