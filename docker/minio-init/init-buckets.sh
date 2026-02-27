#!/bin/sh
set -e

echo "开始初始化 MinIO 存储桶..."

# 等待 MinIO 启动
echo "等待 MinIO 服务启动..."
until mc alias set myminio http://file-service-minio:9000 ${MINIO_ROOT_USER:-fileservice} ${MINIO_ROOT_PASSWORD:-fileservice123}; do
  echo "MinIO 尚未就绪，等待 2 秒后重试..."
  sleep 2
done

echo "✓ MinIO 服务已就绪"

# 创建存储桶
echo "创建存储桶..."

# 创建默认存储桶
if mc mb --ignore-existing myminio/platform-files; then
  echo "✓ 存储桶 platform-files 创建成功"
else
  echo "✗ 存储桶 platform-files 创建失败"
  exit 1
fi

# 创建公开存储桶
if mc mb --ignore-existing myminio/platform-files-public; then
  echo "✓ 存储桶 platform-files-public 创建成功"
else
  echo "✗ 存储桶 platform-files-public 创建失败"
  exit 1
fi

# 创建私有存储桶
if mc mb --ignore-existing myminio/platform-files-private; then
  echo "✓ 存储桶 platform-files-private 创建成功"
else
  echo "✗ 存储桶 platform-files-private 创建失败"
  exit 1
fi

# 设置公开存储桶的访问策略
echo "配置公开存储桶访问策略..."
if mc anonymous set download myminio/platform-files-public; then
  echo "✓ 公开存储桶访问策略设置成功"
else
  echo "✗ 公开存储桶访问策略设置失败"
  exit 1
fi

echo "✓ MinIO 存储桶初始化完成"
echo "  - platform-files (私有)"
echo "  - platform-files-public (公开读取)"
echo "  - platform-files-private (私有)"
