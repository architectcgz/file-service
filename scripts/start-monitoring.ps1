# =====================================================
# File Service Monitoring Startup Script
# =====================================================
# Purpose: Start Prometheus and Grafana monitoring stack
# Requirements: Docker, Docker Compose
# =====================================================

# 设置错误处理
$ErrorActionPreference = "Stop"

# 脚本配置
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_ROOT = Split-Path -Parent $SCRIPT_DIR
$DOCKER_DIR = Join-Path $PROJECT_ROOT "docker"
$MONITORING_COMPOSE = Join-Path $DOCKER_DIR "docker-compose.monitoring.yml"
$BASE_COMPOSE = Join-Path $DOCKER_DIR "docker-compose.yml"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-ColorOutput "✓ $Message" "Green"
}

function Write-Info {
    param([string]$Message)
    Write-ColorOutput "ℹ $Message" "Cyan"
}

function Write-Warning {
    param([string]$Message)
    Write-ColorOutput "⚠ $Message" "Yellow"
}

function Write-Error {
    param([string]$Message)
    Write-ColorOutput "✗ $Message" "Red"
}

# 检查 Docker 是否运行
function Test-DockerRunning {
    try {
        docker info | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

# 检查服务是否运行
function Test-ServiceRunning {
    param([string]$ContainerName)
    
    $container = docker ps --filter "name=$ContainerName" --filter "status=running" --format "{{.Names}}" 2>$null
    return $container -eq $ContainerName
}

# 检查端口是否被占用
function Test-PortInUse {
    param([int]$Port)
    
    $connection = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue
    return $connection.TcpTestSucceeded
}

# 等待服务健康
function Wait-ServiceHealthy {
    param(
        [string]$ContainerName,
        [string]$HealthCheckUrl,
        [int]$MaxRetries = 30,
        [int]$RetryInterval = 2
    )
    
    Write-Info "等待 $ContainerName 服务启动..."
    
    for ($i = 1; $i -le $MaxRetries; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $HealthCheckUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-Success "$ContainerName 服务已就绪"
                return $true
            }
        }
        catch {
            # 继续等待
        }
        
        Write-Host "." -NoNewline
        Start-Sleep -Seconds $RetryInterval
    }
    
    Write-Host ""
    Write-Warning "$ContainerName 服务启动超时，但可能仍在初始化中"
    return $false
}

# 主函数
function Start-Monitoring {
    Write-ColorOutput "`n========================================" "Cyan"
    Write-ColorOutput "  File Service Monitoring Startup" "Cyan"
    Write-ColorOutput "========================================`n" "Cyan"
    
    # 1. 检查 Docker
    Write-Info "检查 Docker 环境..."
    if (-not (Test-DockerRunning)) {
        Write-Error "Docker 未运行，请先启动 Docker Desktop"
        exit 1
    }
    Write-Success "Docker 运行正常"
    
    # 2. 检查配置文件
    Write-Info "检查配置文件..."
    if (-not (Test-Path $MONITORING_COMPOSE)) {
        Write-Error "监控配置文件不存在: $MONITORING_COMPOSE"
        exit 1
    }
    Write-Success "配置文件检查通过"
    
    # 3. 检查端口占用
    Write-Info "检查端口占用情况..."
    $ports = @{
        "9090" = "Prometheus"
        "3100" = "Grafana"
    }
    
    $portsInUse = @()
    foreach ($port in $ports.Keys) {
        if (Test-PortInUse -Port $port) {
            $portsInUse += "$port ($($ports[$port]))"
        }
    }
    
    if ($portsInUse.Count -gt 0) {
        Write-Warning "以下端口已被占用: $($portsInUse -join ', ')"
        Write-Warning "如果这些端口被监控服务占用，将尝试重启服务"
    }
    else {
        Write-Success "所有端口可用"
    }
    
    # 4. 检查基础服务
    Write-Info "检查基础服务状态..."
    $baseServicesRunning = @{
        "file-service-app" = $false
        "file-service-postgres" = $false
        "file-service-redis" = $false
    }
    
    foreach ($service in $baseServicesRunning.Keys) {
        $baseServicesRunning[$service] = Test-ServiceRunning -ContainerName $service
    }
    
    $allBaseServicesRunning = $baseServicesRunning.Values -notcontains $false
    
    if (-not $allBaseServicesRunning) {
        Write-Warning "部分基础服务未运行:"
        foreach ($service in $baseServicesRunning.Keys) {
            if (-not $baseServicesRunning[$service]) {
                Write-Warning "  - $service"
            }
        }
        Write-Info "建议先启动基础服务: docker-compose -f $BASE_COMPOSE up -d"
        
        $response = Read-Host "是否继续启动监控服务? (y/N)"
        if ($response -ne "y" -and $response -ne "Y") {
            Write-Info "已取消启动"
            exit 0
        }
    }
    else {
        Write-Success "所有基础服务运行正常"
    }
    
    # 5. 创建 Docker 网络（如果不存在）
    Write-Info "检查 Docker 网络..."
    $networkExists = docker network ls --filter "name=file-service-network" --format "{{.Name}}" 2>$null
    if (-not $networkExists) {
        Write-Info "创建 Docker 网络: file-service-network"
        docker network create file-service-network | Out-Null
        Write-Success "网络创建成功"
    }
    else {
        Write-Success "网络已存在"
    }
    
    # 6. 启动监控服务
    Write-Info "启动监控服务..."
    Write-Host ""
    
    try {
        Set-Location $DOCKER_DIR
        docker-compose -f docker-compose.monitoring.yml up -d
        
        if ($LASTEXITCODE -ne 0) {
            Write-Error "监控服务启动失败"
            exit 1
        }
    }
    catch {
        Write-Error "启动监控服务时发生错误: $_"
        exit 1
    }
    finally {
        Set-Location $PROJECT_ROOT
    }
    
    Write-Host ""
    Write-Success "监控服务启动命令执行成功"
    
    # 7. 等待服务健康
    Write-Host ""
    $prometheusHealthy = Wait-ServiceHealthy -ContainerName "file-service-prometheus" -HealthCheckUrl "http://localhost:9090/-/healthy"
    $grafanaHealthy = Wait-ServiceHealthy -ContainerName "file-service-grafana" -HealthCheckUrl "http://localhost:3100/api/health"
    
    # 8. 显示服务状态
    Write-Host ""
    Write-ColorOutput "========================================" "Cyan"
    Write-ColorOutput "  监控服务状态" "Cyan"
    Write-ColorOutput "========================================`n" "Cyan"
    
    $services = @(
        @{Name = "file-service-prometheus"; Port = 9090; Url = "http://localhost:9090"; Description = "Prometheus 指标收集" },
        @{Name = "file-service-grafana"; Port = 3100; Url = "http://localhost:3100"; Description = "Grafana 监控仪表板" }
    )
    
    foreach ($service in $services) {
        $running = Test-ServiceRunning -ContainerName $service.Name
        if ($running) {
            Write-Success "$($service.Description)"
            Write-Host "  容器: $($service.Name)" -ForegroundColor Gray
            Write-Host "  地址: $($service.Url)" -ForegroundColor Gray
        }
        else {
            Write-Warning "$($service.Description) - 未运行"
        }
        Write-Host ""
    }
    
    # 9. 显示访问信息
    Write-ColorOutput "========================================" "Green"
    Write-ColorOutput "  访问信息" "Green"
    Write-ColorOutput "========================================`n" "Green"
    
    Write-Host "Prometheus:" -ForegroundColor Cyan
    Write-Host "  URL: http://localhost:9090" -ForegroundColor White
    Write-Host "  Targets: http://localhost:9090/targets" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Grafana:" -ForegroundColor Cyan
    Write-Host "  URL: http://localhost:3100" -ForegroundColor White
    Write-Host "  用户名: admin" -ForegroundColor Gray
    Write-Host "  密码: admin123456" -ForegroundColor Gray
    Write-Host "  仪表板: Bitmap Monitoring (自动加载)" -ForegroundColor Gray
    Write-Host ""
    
    # 10. 显示有用的命令
    Write-ColorOutput "========================================" "Yellow"
    Write-ColorOutput "  常用命令" "Yellow"
    Write-ColorOutput "========================================`n" "Yellow"
    
    Write-Host "查看日志:" -ForegroundColor Cyan
    Write-Host "  docker logs file-service-prometheus" -ForegroundColor Gray
    Write-Host "  docker logs file-service-grafana" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "停止监控服务:" -ForegroundColor Cyan
    Write-Host "  docker-compose -f docker/docker-compose.monitoring.yml down" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "重启监控服务:" -ForegroundColor Cyan
    Write-Host "  docker-compose -f docker/docker-compose.monitoring.yml restart" -ForegroundColor Gray
    Write-Host ""
    
    Write-ColorOutput "========================================`n" "Green"
    Write-Success "监控服务启动完成！"
}

# 执行主函数
try {
    Start-Monitoring
}
catch {
    Write-Error "脚本执行失败: $_"
    exit 1
}
