# 文件上传服务 - 批量运行所有边界场景测试
# 按顺序运行所有边界测试脚本并生成综合报告

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [switch]$SkipCleanup,
    [switch]$StopOnFailure,
    [switch]$GenerateReport
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$ReportDir = Join-Path $ScriptDir "reports"
$AllResults = @()

# 颜色输出函数
function Write-TestHeader {
    param([string]$Message)
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
}

function Write-TestInfo {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Yellow
}

function Write-TestSuccess {
    param([string]$Message)
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-TestFailure {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

# 测试脚本列表
$TestScripts = @(
    @{
        Name = "断点续传场景"
        Script = "测试-断点续传场景.ps1"
        Description = "测试各种断点续传场景"
    },
    @{
        Name = "网络异常场景"
        Script = "测试-网络异常场景.ps1"
        Description = "测试网络异常情况"
    }
)

# 运行单个测试脚本
function Invoke-TestScript {
    param(
        [hashtable]$TestInfo
    )
    
    Write-TestHeader "运行测试: $($TestInfo.Name)"
    Write-TestInfo $TestInfo.Description
    
    $scriptPath = Join-Path $ScriptDir $TestInfo.Script
    
    if (-not (Test-Path $scriptPath)) {
        Write-TestFailure "测试脚本不存在: $scriptPath"
        return @{
            Name = $TestInfo.Name
            Status = "ERROR"
            Duration = 0
            Error = "脚本文件不存在"
        }
    }
    
    $startTime = Get-Date
    
    try {
        # 构建参数
        $params = @{
            ServiceUrl = $ServiceUrl
            AppId = $AppId
            UserId = $UserId
        }
        
        if ($SkipCleanup) {
            $params.SkipCleanup = $true
        }
        
        # 运行测试脚本
        $output = & $scriptPath @params 2>&1
        
        $duration = ((Get-Date) - $startTime).TotalSeconds
        
        # 检查退出码
        if ($LASTEXITCODE -eq 0) {
            Write-TestSuccess "$($TestInfo.Name) 测试通过 (耗时: $([math]::Round($duration, 2))秒)"
            
            return @{
                Name = $TestInfo.Name
                Status = "PASS"
                Duration = [math]::Round($duration, 2)
                Error = $null
                Output = $output
            }
        }
        else {
            Write-TestFailure "$($TestInfo.Name) 测试失败 (退出码: $LASTEXITCODE)"
            
            return @{
                Name = $TestInfo.Name
                Status = "FAIL"
                Duration = [math]::Round($duration, 2)
                Error = "退出码: $LASTEXITCODE"
                Output = $output
            }
        }
    }
    catch {
        $duration = ((Get-Date) - $startTime).TotalSeconds
        
        Write-TestFailure "$($TestInfo.Name) 测试异常: $($_.Exception.Message)"
        
        return @{
            Name = $TestInfo.Name
            Status = "ERROR"
            Duration = [math]::Round($duration, 2)
            Error = $_.Exception.Message
            Output = $null
        }
    }
}

# 生成测试报告
function New-TestReport {
    Write-TestInfo "生成测试报告..."
    
    if (-not (Test-Path $ReportDir)) {
        New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
    }
    
    $reportFile = Join-Path $ReportDir "boundary_tests_report_$Timestamp.md"
    
    $report = @"
# 文件上传服务 - 边界场景测试报告

**测试日期**: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
**服务地址**: $ServiceUrl
**App ID**: $AppId
**User ID**: $UserId

## 测试总结

"@
    
    $totalTests = $AllResults.Count
    $passedTests = ($AllResults | Where-Object { $_.Status -eq "PASS" }).Count
    $failedTests = ($AllResults | Where-Object { $_.Status -eq "FAIL" }).Count
    $errorTests = ($AllResults | Where-Object { $_.Status -eq "ERROR" }).Count
    $totalDuration = ($AllResults | Measure-Object -Property Duration -Sum).Sum
    
    $report += @"

- **总测试套件数**: $totalTests
- **通过**: $passedTests
- **失败**: $failedTests
- **错误**: $errorTests
- **成功率**: $([math]::Round(($passedTests / $totalTests) * 100, 2))%
- **总耗时**: $([math]::Round($totalDuration, 2))秒

## 测试结果

| 测试套件 | 状态 | 耗时(秒) | 错误信息 |
|---------|------|---------|---------|

"@
    
    foreach ($result in $AllResults) {
        $statusIcon = switch ($result.Status) {
            "PASS" { "✅" }
            "FAIL" { "❌" }
            "ERROR" { "⚠️" }
            default { "❓" }
        }
        
        $error = if ($result.Error) { $result.Error } else { "-" }
        $report += "| $statusIcon $($result.Name) | $($result.Status) | $($result.Duration) | $error |`n"
    }
    
    $report += @"

## 详细输出

"@
    
    foreach ($result in $AllResults) {
        $report += @"

### $($result.Name)

**状态**: $($result.Status)
**耗时**: $($result.Duration)秒

"@
        
        if ($result.Error) {
            $report += @"
**错误**: $($result.Error)

"@
        }
        
        if ($result.Output) {
            $report += @"
**输出**:
``````
$($result.Output -join "`n")
``````

"@
        }
    }
    
    $report += @"

## 建议

"@
    
    if ($failedTests -eq 0 -and $errorTests -eq 0) {
        $report += "- ✅ 所有测试通过,系统边界场景处理正常`n"
    }
    else {
        $report += "- ⚠️ 存在失败或错误的测试,请检查详细输出并修复问题`n"
    }
    
    if ($totalDuration -gt 300) {
        $report += "- ⚠️ 测试总耗时较长($([math]::Round($totalDuration / 60, 2))分钟),考虑优化测试或并行执行`n"
    }
    
    $report += @"

---

*报告由边界测试套件自动生成*
"@
    
    [System.IO.File]::WriteAllText($reportFile, $report)
    Write-TestSuccess "报告已保存: $reportFile"
}

# 主测试流程
function Start-AllTests {
    Write-TestHeader "文件上传服务 - 批量边界场景测试"
    
    Write-Host "配置:" -ForegroundColor Cyan
    Write-Host "  服务地址: $ServiceUrl" -ForegroundColor Gray
    Write-Host "  App ID: $AppId" -ForegroundColor Gray
    Write-Host "  User ID: $UserId" -ForegroundColor Gray
    Write-Host "  测试套件数: $($TestScripts.Count)" -ForegroundColor Gray
    Write-Host "  失败时停止: $StopOnFailure" -ForegroundColor Gray
    Write-Host "  生成报告: $GenerateReport" -ForegroundColor Gray
    Write-Host ""
    
    # 验证服务可用性
    Write-TestInfo "验证服务可用性..."
    try {
        $health = Invoke-RestMethod -Uri "$ServiceUrl/actuator/health" -Method Get -TimeoutSec 5
        if ($health.status -ne "UP") {
            Write-TestFailure "服务状态异常: $($health.status)"
            exit 1
        }
        Write-TestSuccess "服务健康检查通过"
    }
    catch {
        Write-TestFailure "服务不可用: $_"
        Write-TestInfo "请确保File Service正在运行: mvn spring-boot:run"
        exit 1
    }
    
    # 运行所有测试
    $testNumber = 1
    foreach ($testInfo in $TestScripts) {
        Write-Host "`n[$testNumber/$($TestScripts.Count)] " -NoNewline -ForegroundColor Cyan
        
        $result = Invoke-TestScript -TestInfo $testInfo
        $script:AllResults += $result
        
        # 如果失败且设置了StopOnFailure,则停止
        if ($StopOnFailure -and $result.Status -in @("FAIL", "ERROR")) {
            Write-TestFailure "测试失败,停止执行"
            break
        }
        
        $testNumber++
        
        # 测试之间留出间隔
        if ($testNumber -le $TestScripts.Count) {
            Write-TestInfo "等待5秒后继续下一个测试..."
            Start-Sleep -Seconds 5
        }
    }
    
    # 显示总结
    Write-TestHeader "测试总结"
    
    $totalTests = $AllResults.Count
    $passedTests = ($AllResults | Where-Object { $_.Status -eq "PASS" }).Count
    $failedTests = ($AllResults | Where-Object { $_.Status -eq "FAIL" }).Count
    $errorTests = ($AllResults | Where-Object { $_.Status -eq "ERROR" }).Count
    $totalDuration = ($AllResults | Measure-Object -Property Duration -Sum).Sum
    
    Write-Host "总测试套件数: $totalTests" -ForegroundColor Cyan
    Write-Host "通过: $passedTests" -ForegroundColor Green
    Write-Host "失败: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })
    Write-Host "错误: $errorTests" -ForegroundColor $(if ($errorTests -gt 0) { "Red" } else { "Green" })
    Write-Host "成功率: $([math]::Round(($passedTests / $totalTests) * 100, 2))%" -ForegroundColor Cyan
    Write-Host "总耗时: $([math]::Round($totalDuration, 2))秒 ($([math]::Round($totalDuration / 60, 2))分钟)" -ForegroundColor Cyan
    
    # 显示详细结果
    Write-Host "`n详细结果:" -ForegroundColor Cyan
    $AllResults | Select-Object Name, Status, Duration, Error | Format-Table -AutoSize
    
    # 生成报告
    if ($GenerateReport) {
        New-TestReport
    }
    
    Write-Host "`n测试执行完成!" -ForegroundColor Cyan
    
    # 返回退出码
    if ($failedTests -gt 0 -or $errorTests -gt 0) {
        exit 1
    }
}

# 运行测试
Start-AllTests
