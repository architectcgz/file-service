# 文件上传服务 - 边界场景测试套件

本目录包含文件上传服务的全面边界场景测试脚本,用于验证系统在各种异常和边界条件下的行为。

---

## 测试脚本列表

### 1. 测试-断点续传场景.ps1

测试各种断点续传场景,确保用户可以在上传中断后恢复上传。

**测试场景**:
- 主动暂停后继续
- 模拟网络中断后恢复
- 重复初始化同一文件
- 分片乱序上传
- 重复上传同一分片(幂等性)

**运行方式**:
```powershell
cd file-service/file-service/test
.\测试-断点续传场景.ps1
```

**自定义参数**:
```powershell
.\测试-断点续传场景.ps1 `
    -ServiceUrl "http://localhost:8089" `
    -AppId "test-app" `
    -UserId "test-user" `
    -TestFileSize 20MB `
    -SkipCleanup
```

### 2. 测试-网络异常场景.ps1

测试各种网络异常情况,验证系统的健壮性和错误处理。

**测试场景**:
- 初始化请求超时
- 获取预签名URL超时
- S3上传超时
- 预签名URL过期
- 完成上传时网络异常

**运行方式**:
```powershell
.\测试-网络异常场景.ps1
```

### 3. 测试-资源限制场景.ps1

测试系统在资源受限时的行为,验证限制和错误处理。

**测试场景**:
- 文件大小超过限制
- 分片数量超过限制
- 单个分片大小不符合要求
- 并发上传数量限制
- 存储空间不足

**运行方式**:
```powershell
.\测试-资源限制场景.ps1
```

### 4. 测试-数据完整性场景.ps1

测试数据完整性验证,确保上传的文件完整无损。

**测试场景**:
- ETag不匹配
- 分片顺序验证
- 缺少部分分片
- 文件Hash验证
- 文件大小验证

**运行方式**:
```powershell
.\测试-数据完整性场景.ps1
```

### 5. 测试-异常状态场景.ps1

测试各种异常状态,验证系统的状态管理和错误处理。

**测试场景**:
- 任务不存在
- 任务已完成
- 任务已中止
- 权限验证
- Redis降级

**运行方式**:
```powershell
.\测试-异常状态场景.ps1
```

### 6. 测试-性能边界场景.ps1

测试系统在极限负载下的性能表现。

**测试场景**:
- 超大文件上传(>1GB)
- 大量分片处理(>100)
- 高并发场景(>50用户)
- 长时间运行测试
- 快速连续请求

**运行方式**:
```powershell
.\测试-性能边界场景.ps1 -ConcurrentUsers 50
```

### 7. 测试-清理和恢复场景.ps1

测试系统的清理和恢复机制。

**测试场景**:
- 任务过期清理
- 中止任务清理
- 系统重启恢复
- Redis数据丢失恢复
- 清理失败处理

**运行方式**:
```powershell
.\测试-清理和恢复场景.ps1
```

---

## 运行所有测试

### 方式1: 手动运行每个脚本

```powershell
cd file-service/file-service/test

# 运行所有测试
.\测试-断点续传场景.ps1
.\测试-网络异常场景.ps1
.\测试-资源限制场景.ps1
.\测试-数据完整性场景.ps1
.\测试-异常状态场景.ps1
.\测试-性能边界场景.ps1
.\测试-清理和恢复场景.ps1
```

### 方式2: 使用批量运行脚本

```powershell
.\运行所有边界测试.ps1
```

---

## 前置条件

### 1. 启动基础设施

```powershell
cd file-service/docker
docker-compose up -d
```

验证服务:
```powershell
# PostgreSQL
docker exec -it file-service-postgres pg_isready -U postgres

# Redis
docker exec -it file-service-redis redis-cli -a redis123456 ping

# RustFS
curl http://localhost:9001/minio/health/live
```

### 2. 启动File Service

```powershell
cd file-service/file-service
mvn spring-boot:run
```

验证服务:
```powershell
curl http://localhost:8089/actuator/health
```

---

## 测试配置

### 通用参数

所有测试脚本支持以下参数:

| 参数 | 默认值 | 说明 |
|------|--------|------|
| ServiceUrl | http://localhost:8089 | File Service地址 |
| AppId | test-app | 应用ID |
| UserId | test-user | 用户ID |
| TestFileSize | 10MB-20MB | 测试文件大小(因脚本而异) |
| SkipCleanup | false | 跳过清理临时文件 |

### 环境变量

可以通过环境变量配置:

```powershell
$env:FILE_SERVICE_URL = "http://localhost:8089"
$env:FILE_SERVICE_APP_ID = "test-app"
$env:FILE_SERVICE_USER_ID = "test-user"
```

---

## 测试结果

### 输出格式

每个测试脚本会输出:
- 实时测试进度
- 每个场景的通过/失败状态
- 详细的错误信息
- 测试总结表格

### 示例输出

```
========================================
测试场景1: 主动暂停后继续
========================================

[INFO] 第一次上传: 上传部分分片...
[INFO] TaskId: 01JCXXX..., 总分片数: 4
[INFO]   分片 1/4 上传完成
[INFO]   分片 2/4 上传完成
[INFO] 模拟暂停,已上传 2 个分片
[INFO] 第二次上传: 续传剩余分片...
[PASS] 断点续传识别成功,已完成 2 个分片
[INFO]   分片 3/4 上传完成
[INFO]   分片 4/4 上传完成
[PASS] 上传完成,文件ID: 01JCYYY...

========================================
测试总结
========================================

总测试数: 5
通过: 5
失败: 0
成功率: 100.00%

详细结果:
TestName              Status Details                                    Error
--------              ------ -------                                    -----
主动暂停后继续        PASS   成功识别并续传 2 个分片                    
网络中断后恢复        PASS   成功从网络中断中恢复并完成上传            
重复初始化同一文件    PASS   正确返回已存在的任务,已完成分片: 1        
分片乱序上传          PASS   成功处理乱序上传的分片                    
重复上传同一分片      PASS   正确处理重复上传的分片                    
```

### 退出码

- 0: 所有测试通过
- 1: 至少有一个测试失败

---

## 故障排查

### 常见问题

#### 1. 服务不可用

**错误**: "服务不可用" 或 "Connection refused"

**解决方案**:
```powershell
# 检查服务状态
curl http://localhost:8089/actuator/health

# 如果服务未启动
cd file-service/file-service
mvn spring-boot:run
```

#### 2. 数据库连接失败

**错误**: "数据库连接失败"

**解决方案**:
```powershell
# 检查PostgreSQL
docker exec -it file-service-postgres pg_isready -U postgres

# 重启PostgreSQL
docker-compose restart postgres
```

#### 3. Redis连接失败

**错误**: "Redis连接失败"

**解决方案**:
```powershell
# 检查Redis
docker exec -it file-service-redis redis-cli -a redis123456 ping

# 重启Redis
docker-compose restart redis
```

#### 4. S3上传失败

**错误**: "S3上传失败" 或 "403 Forbidden"

**解决方案**:
```powershell
# 检查RustFS
curl http://localhost:9001/minio/health/live

# 检查S3配置
# 查看 application.yml 中的 storage.s3 配置

# 重启RustFS
docker-compose restart rustfs
```

#### 5. 测试超时

**错误**: "测试超时"

**解决方案**:
- 增加超时时间参数
- 检查网络连接
- 检查系统资源(CPU/内存)
- 减小测试文件大小

---

## 最佳实践

### 1. 测试前准备

- 确保所有服务正常运行
- 清理之前的测试数据
- 检查系统资源充足

### 2. 测试执行

- 按顺序运行测试(从简单到复杂)
- 每个测试之间留出间隔
- 监控系统资源使用

### 3. 测试后清理

- 清理测试数据
- 检查日志文件
- 记录测试结果

### 4. 持续集成

将测试集成到CI/CD流程:

```yaml
# .github/workflows/boundary-tests.yml
name: Boundary Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v2
      - name: Start Infrastructure
        run: docker-compose up -d
      - name: Run Boundary Tests
        run: |
          cd file-service/file-service/test
          .\运行所有边界测试.ps1
```

---

## 测试覆盖率

### 当前覆盖的场景

- ✅ 断点续传(5个场景)
- ✅ 网络异常(5个场景)
- ✅ 资源限制(5个场景)
- ✅ 数据完整性(5个场景)
- ✅ 异常状态(5个场景)
- ✅ 性能边界(5个场景)
- ✅ 清理和恢复(5个场景)

### 待添加的场景

- ⏳ 并发冲突详细测试
- ⏳ 安全性测试(权限、注入等)
- ⏳ 兼容性测试(不同客户端)
- ⏳ 压力测试(极限负载)

---

## 相关文档

- [API文档](../API.md)
- [直传上传测试指南](../../docs/DIRECT-UPLOAD-TESTING.md)
- [负载测试指南](./README-LOAD-TESTING.md)
- [Docker部署文档](../../docker/README.md)
- [需求文档](../../../.kiro/specs/file-service-boundary-testing/requirements.md)

---

## 贡献指南

### 添加新测试场景

1. 创建新的测试脚本文件(使用中文命名)
2. 遵循现有脚本的结构和风格
3. 添加详细的注释和错误处理
4. 更新本README文档
5. 提交PR并说明测试场景

### 测试脚本模板

```powershell
# 文件上传服务 - [场景名称]测试脚本
# [场景描述]

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"
$TestResults = @()

# 颜色输出函数
function Write-TestHeader { ... }
function Write-TestInfo { ... }
function Write-TestSuccess { ... }
function Write-TestFailure { ... }

# 测试场景函数
function Test-Scenario1 {
    Write-TestHeader "测试场景1: [场景名称]"
    
    try {
        # 测试逻辑
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "[场景名称]"
            Status = "PASS"
            Details = "[详细信息]"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "[场景名称]"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 主测试流程
function Start-Tests {
    Write-TestHeader "[测试套件名称]"
    
    # 运行所有测试
    Test-Scenario1
    
    # 显示总结
    Write-TestHeader "测试总结"
    $TestResults | Format-Table -AutoSize
}

Start-Tests
```

---

**最后更新**: 2026-02-13
**维护者**: 开发团队
