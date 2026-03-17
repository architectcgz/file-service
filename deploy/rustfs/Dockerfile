# 使用rustfs官方镜像
FROM rustfs/rustfs:latest

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /data /logs

# 设置权限
RUN chmod 755 /data /logs

# 暴露端口
EXPOSE 9000

# 使用默认的ENTRYPOINT和CMD
