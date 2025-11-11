# ============================================
# File Service - Dockerfile
# ============================================
# 说明：使用预先发布的文件构建镜像
# 使用方法：
# 1. 在 file-service 根目录执行: dotnet publish -c Release -o ./deploy/publish
# 2. 在 deploy 目录执行: docker build -t file-service:latest .
# ============================================

FROM mcr.microsoft.com/dotnet/aspnet:9.0
WORKDIR /app

# 配置阿里云 Debian 镜像源（加速包下载，如果需要）
# 注意：mcr.microsoft.com/dotnet/aspnet:9.0 基于 Debian，可根据需要配置
# RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources || \
#     sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list || true

# 安装 curl（用于健康检查）
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 复制预先发布的文件（与 Dockerfile 同级的 publish 文件夹）
COPY publish/ ./

# 设置环境变量
ENV ASPNETCORE_ENVIRONMENT=Production
ENV ASPNETCORE_URLS=http://+:5003

# 暴露端口
EXPOSE 5003

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:5003/health || exit 1

# 设置入口点
ENTRYPOINT ["dotnet", "file-service.dll"]

