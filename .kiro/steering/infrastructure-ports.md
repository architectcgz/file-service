# File Service Infrastructure Port Configuration

## Overview

This document defines the port mappings and connection details for all infrastructure services in the File Service. Understanding these mappings is critical for proper service configuration and avoiding port conflicts with other platform services.

---

## Port Mapping Concept

Docker port mapping format: `HOST_PORT:CONTAINER_PORT`

- **HOST_PORT**: Port accessible from your local machine (outside Docker)
- **CONTAINER_PORT**: Port used inside the Docker network

**Example**: `5435:5432` means:
- Connect to `localhost:5435` from your local machine
- Connect to `file-service-postgres:5432` from inside Docker containers

---

## File Service Infrastructure Services

### PostgreSQL (File Service Database)

| Property | Value |
|----------|-------|
| Container Name | `file-service-postgres` |
| Image | `postgres:16-alpine` |
| Host Port | `5434` |
| Container Port | `5432` |
| Default User | `postgres` |
| Default Password | `postgres` |
| Default Database | `file_service` |
| Timezone | `Asia/Shanghai` |

**Port Assignment**: PostgreSQL instance #3 (5434)

**Connection Strings**:
- From Host: `jdbc:postgresql://localhost:5434/file_service`
- From Container: `jdbc:postgresql://file-service-postgres:5432/file_service`

**Environment Variables**:
```yaml
DB_HOST: localhost (host) / file-service-postgres (container)
DB_PORT: 5434 (host) / 5432 (container)
DB_USERNAME: postgres
DB_PASSWORD: postgres
```

---

### RustFS (S3-Compatible Object Storage)

| Property | Value |
|----------|-------|
| Container Name | `file-service-rustfs` |
| Image | `rustfs/rustfs:latest` |
| API Port (Host) | `9001` |
| API Port (Container) | `9000` |
| Console Port (Host) | `9002` |
| Console Port (Container) | `9001` |
| Access Key | `fileservice` |
| Secret Key | `fileservice123` |

**CRITICAL PORT MAPPING**:
- **API from Host Machine**: Use port `9001`
- **API from Docker Containers**: Use port `9000`
- **Console from Host**: Use port `9002`

**Connection Strings**:
- API from Host: `http://localhost:9001`
- API from Container: `http://file-service-rustfs:9000`
- Console: `http://localhost:9002`

**Environment Variables**:
```yaml
# For local development (outside Docker)
S3_ENDPOINT: http://localhost:9001
S3_ACCESS_KEY: fileservice
S3_SECRET_KEY: fileservice123
S3_BUCKET: platform-files
S3_REGION: us-east-1

# For Docker deployment (inside containers)
S3_ENDPOINT: http://file-service-rustfs:9000
S3_ACCESS_KEY: fileservice
S3_SECRET_KEY: fileservice123
S3_BUCKET: platform-files
S3_REGION: us-east-1
```

---

### File Service Application

| Property | Value |
|----------|-------|
| Container Name | `file-service-app` |
| Image | `file-service:latest` |
| Port | `8089` |
| JVM Memory | `-Xms256m -Xmx512m -XX:+UseG1GC` |
| Max File Size | `104857600` (100MB) |
| Storage Type | `s3` |

**Access URLs**:
- API Base: `http://localhost:8089`
- Health Check: `http://localhost:8089/actuator/health`
- Swagger UI: `http://localhost:8089/swagger-ui.html`

**Environment Variables**:
```yaml
SERVER_PORT: 8089
SPRING_PROFILES_ACTIVE: docker
STORAGE_TYPE: s3
MAX_FILE_SIZE: 104857600
```

---

## External Service Ports (DO NOT USE)

These ports are used by other platform services. **DO NOT** assign these ports to File Service to avoid conflicts:

### Blog Microservice
- **PostgreSQL**: `5432` (blog-postgres)
- **Redis**: `6379` (blog-redis)
- **MySQL (Nacos)**: `3307` (blog-mysql-nacos)
- **Nacos**: `8848`, `9848`, `9849` (blog-nacos)
- **RocketMQ NameServer**: `9876` (blog-rocketmq-namesrv)
- **RocketMQ Broker**: `10909`, `10911`, `10912` (blog-rocketmq-broker)
- **RocketMQ Dashboard**: `8180` (blog-rocketmq-dashboard)
- **Elasticsearch**: `9200`, `9300` (blog-elasticsearch)
- **Kibana**: `5601` (blog-kibana)
- **Prometheus**: `9090` (blog-prometheus)
- **Grafana**: `3100` (blog-grafana)
- **SkyWalking OAP**: `11800`, `12800` (blog-skywalking-oap)
- **SkyWalking UI**: `8088` (blog-skywalking-ui)
- **Microservices**: `8000`, `8010`, `8081-8088`, `8090` (various blog services)

### ID Generator Service
- **PostgreSQL**: `5435` (id-generator-postgres)
- **ZooKeeper**: `2181`, `8888` (id-generator-zookeeper)
- **ID Generator Server**: `8010` (id-generator-server)

### IM System
- **PostgreSQL**: `5433` (im-postgres)
- **Redis**: `6380` (im-redis)
- **RocketMQ NameServer**: `9877` (im-rocketmq-nameserver)
- **RocketMQ Broker**: `10913`, `10915`, `10916`, `8080`, `8081` (im-rocketmq-broker)
- **RocketMQ Dashboard**: `8082` (im-rocketmq-dashboard)

### Reserved Port Ranges
- **5432-5435**: PostgreSQL instances (5432=blog, 5433=im, 5434=file, 5435=id-gen)
- **6379-6381**: Redis instances (6379=blog, 6380=im, 6381=reserved)
- **2181**: ZooKeeper
- **3100, 3307**: MySQL/Grafana
- **5601**: Kibana
- **8000-8088, 8090**: Various application services (8089 is File Service)
- **8848-8849**: Nacos
- **8888**: ZooKeeper Admin
- **9090**: Prometheus
- **9200, 9300**: Elasticsearch
- **9848-9849**: Nacos gRPC
- **9876-9877**: RocketMQ NameServer
- **10909-10912**: RocketMQ Broker
- **11800, 12800**: SkyWalking

---

## Configuration Best Practices

### 1. Environment-Specific Configuration

Use environment variables with defaults:

```yaml
# Good - Works in both environments
s3:
  endpoint: ${S3_ENDPOINT:http://localhost:9001}
  access-key: ${S3_ACCESS_KEY:fileservice}
  secret-key: ${S3_SECRET_KEY:fileservice123}
```

### 2. Docker Deployment

For services running in Docker, override with container names:

```yaml
# docker-compose.yml
environment:
  - S3_ENDPOINT=http://file-service-rustfs:9000
  - DB_HOST=file-service-postgres
  - DB_PORT=5432
```

### 3. Local Development

For local development (services running outside Docker):

```yaml
# application-dev.yml or .env
S3_ENDPOINT=http://localhost:9001
DB_HOST=localhost
DB_PORT=5435
```

---

## Storage Configuration

### S3-Compatible Storage (RustFS)

**Bucket Configuration**:
- Default Bucket: `platform-files`
- Region: `us-east-1`
- Path Style Access: Enabled

**Access Control**:
- Public Read: Disabled by default
- Signed URLs: Enabled for secure access
- URL Expiration: Configurable (default 1 hour)

**File Upload Limits**:
- Max File Size: 100MB (configurable via `MAX_FILE_SIZE`)
- Allowed Extensions: Configurable per tenant
- Virus Scanning: Optional integration

---

## Troubleshooting

### PostgreSQL Connection Issues

**Symptom**: `Connection refused` or `database does not exist`

**Solution**:
```bash
# Test connection from host
psql -h localhost -p 5434 -U postgres -d file_service

# Test from container
docker exec file-service-postgres psql -U postgres -d file_service -c "SELECT 1"

# Check logs
docker logs file-service-postgres
```

### RustFS Connection Issues

**Symptom**: `Connection refused` or S3 client errors

**Solution**:
```bash
# Check RustFS health
curl http://localhost:9001/minio/health/live

# Check from container
docker exec file-service-app curl http://file-service-rustfs:9000/minio/health/live

# Check logs
docker logs file-service-rustfs
```

### File Service Application Issues

**Symptom**: Service not starting or health check failing

**Solution**:
```bash
# Check application logs
docker logs file-service-app

# Check health endpoint
curl http://localhost:8089/actuator/health

# Check environment variables
docker exec file-service-app env | grep -E "DB_|S3_"
```

---

## Quick Reference

### Start All Services
```bash
cd docker
docker-compose up -d
```

### Start Individual Services
```bash
# Start only infrastructure
docker-compose up -d file-service-postgres file-service-rustfs

# Start application
docker-compose up -d file-service
```

### Check Service Health
```bash
# Check all containers
docker ps

# Check PostgreSQL
docker exec file-service-postgres pg_isready -U postgres -d file_service

# Check RustFS
curl http://localhost:9001/minio/health/live

# Check File Service
curl http://localhost:8089/actuator/health
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker logs -f file-service-app
docker logs -f file-service-postgres
docker logs -f file-service-rustfs
```

### Stop All Services
```bash
cd docker
docker-compose down
```

### Clean Up (Remove Volumes)
```bash
cd docker
docker-compose down -v
```

---

## Important Notes

1. **Port Allocation Strategy**: Services use sequential port numbers for the same type of infrastructure:
   - PostgreSQL: 5432 (blog), 5433 (im), 5434 (file), 5435 (id-gen)
   - Redis: 6379 (blog), 6380 (im), 6381 (reserved)

2. **PostgreSQL Port**: Use `5434` from host, `5432` from containers
3. **RustFS API Port**: Use `9001` from host, `9000` from containers
4. **RustFS Console**: Access at `http://localhost:9002`
5. **File Service Port**: `8089` (same for host and container)
6. **Network**: All services must be on the `file-service-network` Docker network
7. **Storage**: Files are stored in RustFS (S3-compatible), not local filesystem
8. **Max File Size**: Default 100MB, configurable via environment variable

---

## Integration with Other Services

### Using File Service from Blog Microservice

```yaml
# In blog-microservice application.yml
file-service:
  url: http://localhost:8089
  tenant-id: blog-platform
```

### Using File Service from IM System

```yaml
# In im-system application.yml
file-service:
  url: http://localhost:8089
  tenant-id: im-platform
```

---

## Related Documentation

- [File Service Client README](../file-service-client/README.md)
- [File Service Example](../examples/file-service-example/README.md)
- [Docker Compose Configuration](../docker/docker-compose.yml)
