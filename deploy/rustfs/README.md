# RustFS 部署说明

## 查看日志

### 查看容器日志

```bash
# 查看 RustFS 容器日志
docker logs rustfs

# 实时查看日志
docker logs -f rustfs

# 查看最近 100 行日志
docker logs --tail 100 rustfs

# 查看 RustFS Nginx 容器日志
docker logs rustfs-nginx
docker logs -f rustfs-nginx
```

### 查看挂载的日志文件

日志文件挂载在 `./logs/` 目录：

```bash
# RustFS 应用日志
cat logs/rustfs.log_*.log

# Nginx 日志
tail -f logs/nginx/access.log
tail -f logs/nginx/error.log
```

### 进入容器查看

```bash
# 进入 RustFS 容器
docker exec -it rustfs sh

# 进入 RustFS Nginx 容器
docker exec -it rustfs-nginx sh
```

## 健康检查

如果健康检查失败，可以：

1. **检查容器状态**
   ```bash
   docker ps -a
   docker inspect rustfs | grep -A 10 Health
   ```

2. **手动测试健康检查**
   ```bash
   # 进入容器测试
   docker exec rustfs nc -z localhost 9000
   # 或者
   docker exec rustfs wget -O- http://localhost:9000/minio/health/live
   ```

3. **临时禁用健康检查**
   如果服务已正常运行但健康检查失败，可以临时注释掉 `depends_on` 中的 `condition: service_healthy`

## 常见问题

### 1. 健康检查失败

- 检查 RustFS 是否正常启动：`docker logs rustfs`
- 检查端口是否开放：`docker exec rustfs nc -z localhost 9000`
- 增加 `start_period` 时间，给服务更多启动时间

### 2. 容器无法启动

- 检查数据目录权限：`ls -la ./data`
- 检查配置文件：`ls -la ./config`
- 查看详细错误：`docker logs rustfs`

### 3. 网络连接问题

- 检查网络：`docker network ls`
- 检查容器网络：`docker inspect rustfs | grep NetworkMode`

## 部署命令

```bash
# 启动服务
docker compose up -d

# 查看服务状态
docker compose ps

# 停止服务
docker compose down

# 重启服务
docker compose restart

# 查看日志
docker compose logs -f
```

