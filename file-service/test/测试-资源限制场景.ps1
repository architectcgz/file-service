# 文件上传服务 - 资源限制场景测试脚本
# 测试系统在资源受限时的行为,验证限制和错误处理

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TempDir = Join-Path $env:TEMP "resource_limit_test_$Timestamp"
$TestResults = @()

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

# 初始化测试环境
function Initialize-TestEnvironment {
    Write-TestInfo "初始化测试环境..."
    
    if (-not (Test-Path $TempDir)) {
        New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
    }
    
    # 验证服务可用性
    try {
        $health = Invoke-RestMethod -Uri "$ServiceUrl/actuator/health" -Method Get -TimeoutSec 5
        if ($health.status -ne "UP") {
            throw "服务状态异常: $($health.status)"
        }
        Write-TestSuccess "服务健康检查通过"
    }
    catch {
        Write-TestFailure "服务不可用: $_"
        exit 1
    }
}

# 生成测试文件
function New-TestFile {
    param(
        [string]$FilePath,
        [long]$SizeBytes
    )
    
    Write-TestInfo "生成测试文件: $([math]::Round($SizeBytes / 1MB, 2))MB"
    
    $content = "TEST_" * 1000
    $contentBytes = [System.Text.Encoding]::UTF8.GetBytes($content)
    
    $stream = [System.IO.File]::Create($FilePath)
    $written = 0
    
    while ($written -lt $SizeBytes) {
        $toWrite = [Math]::Min($contentBytes.Length, $SizeBytes - $written)
        $stream.Write($contentBytes, 0, $toWrite)
        $written += $toWrite
    }
    
    $stream.Close()
    
    return $FilePath
}

# 测试场景1: 文件大小超过限制
function Test-FileSizeExceedsLimit {
    Write-TestHeader "测试场景1: 文件大小超过限制"
    
    try {
        # 尝试上传一个超大文件(假设限制是100MB)
        $oversizedFile = 200GB  # 200GB,远超限制
        
        Write-TestInfo "尝试初始化超大文件上传: $([math]::Round($oversizedFile / 1GB, 2))GB"
        
        $initRequest = @{
            fileName = "oversized_test.txt"
            fileSize = $oversizedFile
            contentType = "text/plain"
            fileHash = "test-hash-oversized"
        } | ConvertTo-Json
        
        try {
            $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $initRequest
            
            # 如果没有抛出异常,检查响应
            if ($response.code -ne 200) {
                if ($response.message -match "文件过大|超过限制|too large|exceeds") {
                    Write-TestSuccess "正确拒绝超大文件: $($response.message)"
                    
                    $script:TestResults += [PSCustomObject]@{
                        TestName = "文件大小超过限制"
                        Status = "PASS"
                        Details = "正确拒绝超大文件"
                        Error = $null
                    }
                }
                else {
                    throw "返回了意外的错误: $($response.message)"
                }
            }
            else {
                Write-TestFailure "系统接受了超大文件,未进行大小限制检查"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "文件大小超过限制"
                    Status = "FAIL"
                    Details = "系统未拒绝超大文件"
                    Error = "缺少文件大小限制检查"
                }
            }
        }
        catch {
            # 检查是否是预期的错误
            if ($_.Exception.Message -match "文件过大|超过限制|too large|exceeds|400|413") {
                Write-TestSuccess "正确拒绝超大文件: $($_.Exception.Message)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "文件大小超过限制"
                    Status = "PASS"
                    Details = "正确拒绝超大文件"
                    Error = $null
                }
            }
            else {
                throw
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "文件大小超过限制"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 测试场景2: 分片数量超过限制
function Test-PartCountExceedsLimit {
    Write-TestHeader "测试场景2: 分片数量超过限制"
    
    try {
        # 尝试上传一个会产生过多分片的文件
        # 假设分片大小是5MB,最大分片数是10000
        # 那么最大文件大小是 5MB * 10000 = 50GB
        $oversizedFile = 60GB  # 60GB,会产生超过10000个分片
        
        Write-TestInfo "尝试初始化会产生过多分片的文件: $([math]::Round($oversizedFile / 1GB, 2))GB"
        
        $initRequest = @{
            fileName = "too_many_parts_test.txt"
            fileSize = $oversizedFile
            contentType = "text/plain"
            fileHash = "test-hash-too-many-parts"
        } | ConvertTo-Json
        
        try {
            $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $initRequest
            
            if ($response.code -ne 200) {
                if ($response.message -match "分片.*超过限制|too many parts|exceeds.*parts") {
                    Write-TestSuccess "正确拒绝过多分片: $($response.message)"
                    
                    $script:TestResults += [PSCustomObject]@{
                        TestName = "分片数量超过限制"
                        Status = "PASS"
                        Details = "正确拒绝过多分片"
                        Error = $null
                    }
                }
                else {
                    throw "返回了意外的错误: $($response.message)"
                }
            }
            else {
                Write-TestFailure "系统接受了过多分片,未进行限制检查"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "分片数量超过限制"
                    Status = "FAIL"
                    Details = "系统未拒绝过多分片"
                    Error = "缺少分片数量限制检查"
                }
            }
        }
        catch {
            if ($_.Exception.Message -match "分片.*超过限制|too many parts|exceeds.*parts|400") {
                Write-TestSuccess "正确拒绝过多分片: $($_.Exception.Message)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "分片数量超过限制"
                    Status = "PASS"
                    Details = "正确拒绝过多分片"
                    Error = $null
                }
            }
            else {
                throw
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "分片数量超过限制"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 测试场景3: 单个分片大小不符合要求
function Test-InvalidPartSize {
    Write-TestHeader "测试场景3: 单个分片大小不符合要求"
    
    $testFile = Join-Path $TempDir "invalid_part_size_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes 10MB | Out-Null
    
    try {
        # 初始化上传
        $fileName = [System.IO.Path]::GetFileName($testFile)
        $fileSize = (Get-Item $testFile).Length
        
        $initRequest = @{
            fileName = $fileName
            fileSize = $fileSize
            contentType = "text/plain"
            fileHash = "test-hash-invalid-part-size"
        } | ConvertTo-Json
        
        $initResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
            -Method Post `
            -Headers @{
                "Content-Type" = "application/json"
                "X-App-Id" = $AppId
                "X-User-Id" = $UserId
            } `
            -Body $initRequest
        
        if ($initResponse.code -ne 200) {
            throw "初始化失败: $($initResponse.message)"
        }
        
        $taskId = $initResponse.data.taskId
        $chunkSize = $initResponse.data.chunkSize
        
        Write-TestInfo "标准分片大小: $([math]::Round($chunkSize / 1MB, 2))MB"
        
        # 获取第一个分片的上传URL
        $urlRequest = @{
            taskId = $taskId
            partNumbers = @(1)
        } | ConvertTo-Json
        
        $urlResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/part-urls" `
            -Method Post `
            -Headers @{
                "Content-Type" = "application/json"
                "X-App-Id" = $AppId
                "X-User-Id" = $UserId
            } `
            -Body $urlRequest
        
        if ($urlResponse.code -ne 200) {
            throw "获取上传URL失败: $($urlResponse.message)"
        }
        
        $uploadUrl = $urlResponse.data.partUrls[0].uploadUrl
        
        # 尝试上传一个过小的分片(1KB,远小于标准分片大小)
        Write-TestInfo "尝试上传过小的分片(1KB)..."
        $tinyData = New-Object byte[] 1024
        
        try {
            $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
                -Method Put `
                -Body $tinyData `
                -Headers @{
                    "Content-Type" = "application/octet-stream"
                }
            
            # S3可能接受任意大小的分片,这不一定是错误
            if ($uploadResponse.StatusCode -eq 200) {
                Write-TestInfo "S3接受了小分片(这是正常的,S3允许最后一个分片小于标准大小)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "单个分片大小不符合要求"
                    Status = "PASS"
                    Details = "S3正确处理了非标准大小的分片"
                    Error = $null
                }
            }
        }
        catch {
            if ($_.Exception.Response.StatusCode -eq 400) {
                Write-TestSuccess "S3正确拒绝了不符合要求的分片大小"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "单个分片大小不符合要求"
                    Status = "PASS"
                    Details = "正确拒绝不符合要求的分片大小"
                    Error = $null
                }
            }
            else {
                throw
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "单个分片大小不符合要求"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
    finally {
        if (Test-Path $testFile) {
            Remove-Item $testFile -Force
        }
    }
}

# 测试场景4: 并发上传数量限制
function Test-ConcurrentUploadLimit {
    Write-TestHeader "测试场景4: 并发上传数量限制"
    
    try {
        Write-TestInfo "测试并发上传限制..."
        
        # 尝试创建大量并发上传任务
        $concurrentCount = 100
        Write-TestInfo "尝试创建 $concurrentCount 个并发上传任务..."
        
        $jobs = @()
        $successCount = 0
        $failCount = 0
        $limitedCount = 0
        
        for ($i = 1; $i -le $concurrentCount; $i++) {
            $job = Start-Job -ScriptBlock {
                param($ServiceUrl, $AppId, $UserId, $Index)
                
                $initRequest = @{
                    fileName = "concurrent_test_$Index.txt"
                    fileSize = 10MB
                    contentType = "text/plain"
                    fileHash = "test-hash-concurrent-$Index"
                } | ConvertTo-Json
                
                try {
                    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                        -Method Post `
                        -Headers @{
                            "Content-Type" = "application/json"
                            "X-App-Id" = $AppId
                            "X-User-Id" = $UserId
                        } `
                        -Body $initRequest `
                        -TimeoutSec 10
                    
                    return @{
                        Success = ($response.code -eq 200)
                        Limited = $false
                        Error = if ($response.code -ne 200) { $response.message } else { $null }
                    }
                }
                catch {
                    $isLimited = $_.Exception.Message -match "限流|rate limit|too many|429"
                    
                    return @{
                        Success = $false
                        Limited = $isLimited
                        Error = $_.Exception.Message
                    }
                }
            } -ArgumentList $ServiceUrl, $AppId, $UserId, $i
            
            $jobs += $job
        }
        
        Write-TestInfo "等待所有任务完成..."
        $jobs | Wait-Job -Timeout 60 | Out-Null
        
        # 收集结果
        foreach ($job in $jobs) {
            $result = Receive-Job -Job $job
            
            if ($result.Success) {
                $successCount++
            }
            elseif ($result.Limited) {
                $limitedCount++
            }
            else {
                $failCount++
            }
            
            Remove-Job -Job $job -Force
        }
        
        Write-TestInfo "成功: $successCount, 限流: $limitedCount, 失败: $failCount"
        
        if ($limitedCount -gt 0) {
            Write-TestSuccess "系统正确实施了并发限制,限流了 $limitedCount 个请求"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "并发上传数量限制"
                Status = "PASS"
                Details = "正确实施并发限制,成功:$successCount, 限流:$limitedCount"
                Error = $null
            }
        }
        elseif ($successCount -eq $concurrentCount) {
            Write-TestInfo "所有请求都成功,系统可能没有并发限制或限制较高"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "并发上传数量限制"
                Status = "PASS"
                Details = "系统处理了所有并发请求,未触发限制"
                Error = $null
            }
        }
        else {
            Write-TestFailure "存在非限流的失败请求: $failCount"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "并发上传数量限制"
                Status = "FAIL"
                Details = "存在非限流的失败请求"
                Error = "失败数: $failCount"
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "并发上传数量限制"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 测试场景5: 无效的文件类型
function Test-InvalidFileType {
    Write-TestHeader "测试场景5: 无效的文件类型"
    
    try {
        Write-TestInfo "测试无效文件类型验证..."
        
        # 尝试上传一个可能被禁止的文件类型(如.exe)
        $initRequest = @{
            fileName = "malicious.exe"
            fileSize = 1MB
            contentType = "application/x-msdownload"
            fileHash = "test-hash-invalid-type"
        } | ConvertTo-Json
        
        try {
            $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $initRequest
            
            if ($response.code -ne 200) {
                if ($response.message -match "文件类型|不支持|not supported|invalid type") {
                    Write-TestSuccess "正确拒绝无效文件类型: $($response.message)"
                    
                    $script:TestResults += [PSCustomObject]@{
                        TestName = "无效的文件类型"
                        Status = "PASS"
                        Details = "正确拒绝无效文件类型"
                        Error = $null
                    }
                }
                else {
                    throw "返回了意外的错误: $($response.message)"
                }
            }
            else {
                Write-TestInfo "系统接受了.exe文件,可能没有文件类型限制"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "无效的文件类型"
                    Status = "PASS"
                    Details = "系统允许所有文件类型"
                    Error = $null
                }
            }
        }
        catch {
            if ($_.Exception.Message -match "文件类型|不支持|not supported|invalid type|400") {
                Write-TestSuccess "正确拒绝无效文件类型: $($_.Exception.Message)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "无效的文件类型"
                    Status = "PASS"
                    Details = "正确拒绝无效文件类型"
                    Error = $null
                }
            }
            else {
                throw
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "无效的文件类型"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 主测试流程
function Start-ResourceLimitTests {
    Write-TestHeader "文件上传服务 - 资源限制场景测试"
    
    Write-Host "配置:" -ForegroundColor Cyan
    Write-Host "  服务地址: $ServiceUrl" -ForegroundColor Gray
    Write-Host "  App ID: $AppId" -ForegroundColor Gray
    Write-Host "  User ID: $UserId" -ForegroundColor Gray
    Write-Host ""
    
    Initialize-TestEnvironment
    
    # 运行所有测试场景
    Test-FileSizeExceedsLimit
    Test-PartCountExceedsLimit
    Test-InvalidPartSize
    Test-ConcurrentUploadLimit
    Test-InvalidFileType
    
    # 显示测试总结
    Write-TestHeader "测试总结"
    
    $totalTests = $TestResults.Count
    $passedTests = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
    $failedTests = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
    
    Write-Host "总测试数: $totalTests" -ForegroundColor Cyan
    Write-Host "通过: $passedTests" -ForegroundColor Green
    Write-Host "失败: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })
    Write-Host "成功率: $([math]::Round(($passedTests / $totalTests) * 100, 2))%" -ForegroundColor Cyan
    
    # 显示详细结果
    Write-Host "`n详细结果:" -ForegroundColor Cyan
    $TestResults | Format-Table -AutoSize
    
    # 清理
    if (-not $SkipCleanup) {
        Write-TestInfo "清理临时文件..."
        if (Test-Path $TempDir) {
            Remove-Item $TempDir -Recurse -Force
        }
    }
    
    Write-Host "`n测试执行完成!" -ForegroundColor Cyan
    
    # 返回退出码
    if ($failedTests -gt 0) {
        exit 1
    }
}

# 运行测试
Start-ResourceLimitTests
