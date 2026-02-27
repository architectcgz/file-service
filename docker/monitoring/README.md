# File Service Monitoring Stack

本目录包含 File Service Bitmap 优化功能的监控配置。

## 组件

- **Prometheus**: 指标收集和存储
- **Grafana**: 可视化仪表板

## 快速启动

### 1. 启动基础服务

首先启动 File Service 及其依赖服务：

```powershell
# 在 file-service/docker 目录下
docker-compose up -d
```

### 2. 启动监控服务

```powershell
# 在 file-service/docker 目录下
docker-compose -f docker-compose.monitoring.yml up -d
```

### 3. 访问监控界面

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3100
  - 默认用户名: `admin`
  - 默认密码: `admin123456`

## 配置说明

### Prometheus 配置

配置文件: `monitoring/prometheus.yml`

- **抓取间隔**: 15 秒
- **数据保留**: 30 天
- **监控目标**: File Service (http://host.docker.internal:8089/actuator/prometheus)

### Grafana 配置

#### 数据源

配置文件: `monitoring/grafana/datasources/prometheus.yml`

- 自动配置 Prometheus 作为默认数据源
- 连接地址: http://prometheus:9090

#### 仪表板

配置文件: `monitoring/grafana/dashboards/bitmap-monitoring.json`

自动加载的仪表板包含以下面板：

1. **写入成功率**: Bitmap 写入操作的成功率
2. **缓存命中率**: Redis 缓存的命中率
3. **回退次数**: Redis 故障时回退到数据库的次数
4. **操作延迟**: 各操作的响应时间
5. **同步统计**: 定期同步和全量同步的执行情况
6. **活跃任务数**: 当前正在进行的上传任务数量

## 监控指标

### Bitmap 写入指标

- `bitmap_write_success_total`: 写入成功次数
- `bitmap_write_failure_total`: 写入失败次数
- `bitmap_write_duration_seconds`: 写入操作耗时

### 缓存指标

- `bitmap_cache_hit_total`: 缓存命中次数
- `bitmap_cache_miss_total`: 缓存未命中次数
- `bitmap_cache_hit_ratio`: 缓存命中率 (Gauge)

### 回退指标

- `bitmap_fallback_total`: 回退到数据库的次数

### 同步指标

- `bitmap_sync_total`: 同步操作次数
- `bitmap_sync_duration_seconds`: 同步操作耗时

### 活跃任务指标

- `bitmap_active_tasks`: 当前活跃的上传任务数 (Gauge)

## 使用场景

### 1. 监控 Bitmap 优化效果

查看缓存命中率和数据库回退次数，评估优化效果：

```promql
# 缓存命中率
bitmap_cache_hit_ratio

# 数据库回退率
rate(bitmap_fallback_total[5m]) / rate(bitmap_write_success_total[5m])
```

### 2. 性能分析

分析各操作的响应时间：

```promql
# 写入操作 P95 延迟
histogram_quantile(0.95, rate(bitmap_write_duration_seconds_bucket[5m]))

# 同步操作平均耗时
rate(bitmap_sync_duration_seconds_sum[5m]) / rate(bitmap_sync_duration_seconds_count[5m])
```

### 3. 故障排查

监控异常情况：

```promql
# 写入失败率
rate(bitmap_write_failure_total[5m])

# 回退次数激增
increase(bitmap_fallback_total[1h]) > 100
```

## 告警配置 (可选)

可以在 Prometheus 中配置告警规则，例如：

```yaml
groups:
  - name: bitmap_alerts
    rules:
      - alert: HighWriteFailureRate
        expr: rate(bitmap_write_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Bitmap 写入失败率过高"
          description: "过去 5 分钟写入失败率超过 10%"
      
      - alert: LowCacheHitRate
        expr: bitmap_cache_hit_ratio < 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "缓存命中率过低"
          description: "缓存命中率低于 80%"
```

## 停止监控服务

```powershell
# 停止监控服务
docker-compose -f docker-compose.monitoring.yml down

# 停止并删除数据卷
docker-compose -f docker-compose.monitoring.yml down -v
```

## 故障排查

### Prometheus 无法抓取指标

1. 检查 File Service 是否启动：
   ```powershell
   curl http://localhost:8089/actuator/health
   ```

2. 检查 Prometheus 端点是否可访问：
   ```powershell
   curl http://localhost:8089/actuator/prometheus
   ```

3. 检查 Prometheus 配置：
   - 访问 http://localhost:9090/targets
   - 查看 file-service 目标状态

### Grafana 无法连接 Prometheus

1. 检查 Prometheus 是否运行：
   ```powershell
   docker ps | findstr prometheus
   ```

2. 检查网络连接：
   ```powershell
   docker exec file-service-grafana wget -O- http://file-service-prometheus:9090/-/healthy
   ```

3. 检查数据源配置：
   - 登录 Grafana
   - 进入 Configuration → Data Sources
   - 测试 Prometheus 连接

### 仪表板未自动加载

1. 检查仪表板配置文件是否正确挂载：
   ```powershell
   docker exec file-service-grafana ls -la /etc/grafana/provisioning/dashboards/
   ```

2. 检查 Grafana 日志：
   ```powershell
   docker logs file-service-grafana
   ```

3. 手动导入仪表板：
   - 登录 Grafana
   - 进入 Dashboards → Import
   - 上传 `bitmap-monitoring.json` 文件

## 相关文档

- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Grafana 官方文档](https://grafana.com/docs/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
