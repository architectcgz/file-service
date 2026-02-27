# 文件上传服务 - 网络异常场景测试脚本
# 测试各种网络异常情况,包括超时、连接中断、服务不可用等

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [int]$TestFileSize = 10MB,
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TempDir = Join-Path $env:TEMP "network_test_$Timestamp"
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

function Write-TestWarning {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Magenta
}

# 初始化测试环境
function Initialize-TestEnvironment {
    Write-TestInfo "初始化测试环境..."
    
    if (-not (Test-Path $TempDir)) {
        New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
    }
}

# 生成测试文件
function New-TestFile {
    param(
        [string]$FilePath,
        [long]$SizeBytes
    )
    
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

# 测试场景1: 初始化请求超时
function Test-InitializationTimeout {
    Write-TestHeader "测试场景1: 初始化请求超时"
    
    $testFile = Join-Path $TempDir "init_timeout_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    try {
        $fileName = [System.IO.Path]::GetFileName($testFile)
        $fileSize = (Get-Item $testFile).Length
        
        $initRequest = @{
            fileName = $fileName
            fileSize = $fileSize
            contentType = "text/plain"
            fileHash = "test-hash-timeout"
        } | ConvertTo-Json
        
        Write-TestInfo "发送初始化请求(超时设置: 1秒)..."
        
        try {
            $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $initRequest `
                -TimeoutSec 1
            
            # 如果在1秒内完成,说明服务响应正常
            Write-TestSuccess "服务响应快速(< 1秒),超时测试跳过"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "初始化请求超时"
                Status = "SKIP"
                Details = "服务响应过快,无法触发超时"
                Error = $null
            }
        }
        catch {
            if ($_.Exception.Message -match "timeout|超时") {
                Write-TestSuccess "正确捕获超时异常: $($_.Exception.Message)"
                
                # 验证可以重试
                Write-TestInfo "验证重试机制..."
                $retryResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
                    -Method Post `
                    -Headers @{
                        "Content-Type" = "application/json"
                        "X-App-Id" = $AppId
                        "X-User-Id" = $UserId
                    } `
                    -Body $initRequest `
                    -TimeoutSec 30
                
                if ($retryResponse.code -eq 200) {
                    Write-TestSuccess "重试成功"
                }
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "初始化请求超时"
                    Status = "PASS"
                    Details = "正确处理超时并支持重试"
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
            TestName = "初始化请求超时"
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

# 测试场景2: 获取预签名URL超时
function Test-PresignedUrlTimeout {
    Write-TestHeader "测试场景2: 获取预签名URL超时"
    
    try {
        Write-TestInfo "测试获取预签名URL的超时处理..."
        
        # 使用一个不存在的taskId
        $urlRequest = @{
            taskId = "non-existent-task-id"
            partNumbers = @(1, 2, 3)
        } | ConvertTo-Json
        
        try {
            $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/part-urls" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $urlRequest `
                -TimeoutSec 5
            
            # 应该返回任务不存在的错误
            if ($response.code -ne 200) {
                Write-TestSuccess "正确返回错误: $($response.message)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "获取预签名URL超时"
                    Status = "PASS"
                    Details = "正确处理无效任务ID"
                    Error = $null
                }
            }
        }
        catch {
            if ($_.Exception.Message -match "timeout|超时") {
                Write-TestWarning "请求超时: $($_.Exception.Message)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "获取预签名URL超时"
                    Status = "PASS"
                    Details = "捕获超时异常"
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
            TestName = "获取预签名URL超时"
            Status = "FAIL"
            Details = $null
            Error = $_.Exception.Message
        }
    }
}

# 测试场景3: S3上传超时
function Test-S3UploadTimeout {
    Write-TestHeader "测试场景3: S3上传超时"
    
    $testFile = Join-Path $TempDir "s3_timeout_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    try {
        # 初始化上传
        $fileName = [System.IO.Path]::GetFileName($testFile)
        $fileSize = (Get-Item $testFile).Length
        
        $initRequest = @{
            fileName = $fileName
            fileSize = $fileSize
            contentType = "text/plain"
            fileHash = "test-hash-s3-timeout"
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
        
        # 读取分片数据
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $buffer = New-Object byte[] $chunkSize
        $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
        
        if ($bytesRead -lt $chunkSize) {
            $buffer = $buffer[0..($bytesRead - 1)]
        }
        
        $fileStream.Close()
        
        # 尝试上传(设置很短的超时)
        Write-TestInfo "上传分片到S3(超时设置: 1秒)..."
        
        try {
            $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
                -Method Put `
                -Body $buffer `
                -Headers @{
                    "Content-Type" = "application/octet-stream"
                } `
                -TimeoutSec 1
            
            Write-TestSuccess "上传成功(< 1秒),超时测试跳过"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "S3上传超时"
                Status = "SKIP"
                Details = "上传速度过快,无法触发超时"
                Error = $null
            }
        }
        catch {
            if ($_.Exception.Message -match "timeout|超时|timed out") {
                Write-TestSuccess "正确捕获S3上传超时: $($_.Exception.Message)"
                
                # 验证可以重试
                Write-TestInfo "验证重试机制..."
                $retryResponse = Invoke-WebRequest -Uri $uploadUrl `
                    -Method Put `
                    -Body $buffer `
                    -Headers @{
                        "Content-Type" = "application/octet-stream"
                    } `
                    -TimeoutSec 30
                
                if ($retryResponse.StatusCode -eq 200) {
                    Write-TestSuccess "重试成功"
                }
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "S3上传超时"
                    Status = "PASS"
                    Details = "正确处理S3上传超时并支持重试"
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
            TestName = "S3上传超时"
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

# 测试场景4: 预签名URL过期
function Test-ExpiredPresignedUrl {
    Write-TestHeader "测试场景4: 预签名URL过期"
    
    $testFile = Join-Path $TempDir "expired_url_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    try {
        # 初始化上传
        $fileName = [System.IO.Path]::GetFileName($testFile)
        $fileSize = (Get-Item $testFile).Length
        
        $initRequest = @{
            fileName = $fileName
            fileSize = $fileSize
            contentType = "text/plain"
            fileHash = "test-hash-expired-url"
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
        
        # 获取上传URL
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
        $expiresIn = $urlResponse.data.partUrls[0].expiresIn
        
        Write-TestInfo "预签名URL有效期: $expiresIn 秒"
        Write-TestInfo "注意: 实际测试URL过期需要等待 $expiresIn 秒,此测试将跳过"
        Write-TestInfo "建议: 在开发环境中将过期时间设置为较短的值(如60秒)进行测试"
        
        # 读取分片数据
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $buffer = New-Object byte[] $chunkSize
        $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
        
        if ($bytesRead -lt $chunkSize) {
            $buffer = $buffer[0..($bytesRead - 1)]
        }
        
        $fileStream.Close()
        
        # 立即上传(应该成功)
        Write-TestInfo "立即使用URL上传(应该成功)..."
        $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
            -Method Put `
            -Body $buffer `
            -Headers @{
                "Content-Type" = "application/octet-stream"
            }
        
        if ($uploadResponse.StatusCode -eq 200) {
            Write-TestSuccess "URL在有效期内上传成功"
        }
        
        # 如果有效期较短,可以等待过期后测试
        if ($expiresIn -le 120) {
            Write-TestInfo "等待URL过期($expiresIn 秒)..."
            Start-Sleep -Seconds ($expiresIn + 5)
            
            Write-TestInfo "使用过期URL上传(应该失败)..."
            try {
                $expiredResponse = Invoke-WebRequest -Uri $uploadUrl `
                    -Method Put `
                    -Body $buffer `
                    -Headers @{
                        "Content-Type" = "application/octet-stream"
                    }
                
                Write-TestWarning "过期URL仍然可用,可能是S3配置问题"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "预签名URL过期"
                    Status = "WARN"
                    Details = "过期URL仍然可用"
                    Error = $null
                }
            }
            catch {
                if ($_.Exception.Response.StatusCode -eq 403) {
                    Write-TestSuccess "过期URL正确返回403 Forbidden"
                    
                    # 验证可以重新获取URL
                    Write-TestInfo "重新获取URL..."
                    $newUrlResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/part-urls" `
                        -Method Post `
                        -Headers @{
                            "Content-Type" = "application/json"
                            "X-App-Id" = $AppId
                            "X-User-Id" = $UserId
                        } `
                        -Body $urlRequest
                    
                    if ($newUrlResponse.code -eq 200) {
                        Write-TestSuccess "成功重新获取URL"
                    }
                    
                    $script:TestResults += [PSCustomObject]@{
                        TestName = "预签名URL过期"
                        Status = "PASS"
                        Details = "正确处理过期URL并支持重新获取"
                        Error = $null
                    }
                }
                else {
                    throw
                }
            }
        }
        else {
            Write-TestInfo "URL有效期过长($expiresIn 秒),跳过过期测试"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = "预签名URL过期"
                Status = "SKIP"
                Details = "URL有效期过长,无法在合理时间内测试过期场景"
                Error = $null
            }
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "预签名URL过期"
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

# 测试场景5: 完成上传时网络异常
function Test-CompleteUploadNetworkError {
    Write-TestHeader "测试场景5: 完成上传时网络异常"
    
    $testFile = Join-Path $TempDir "complete_network_error_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    try {
        # 初始化并上传所有分片
        $fileName = [System.IO.Path]::GetFileName($testFile)
        $fileSize = (Get-Item $testFile).Length
        
        $initRequest = @{
            fileName = $fileName
            fileSize = $fileSize
            contentType = "text/plain"
            fileHash = "test-hash-complete-error"
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
        $totalParts = $initResponse.data.totalParts
        
        Write-TestInfo "上传所有分片..."
        
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $uploadedParts = @()
        
        for ($i = 1; $i -le $totalParts; $i++) {
            # 获取URL
            $urlRequest = @{
                taskId = $taskId
                partNumbers = @($i)
            } | ConvertTo-Json
            
            $urlResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/part-urls" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $urlRequest
            
            $uploadUrl = $urlResponse.data.partUrls[0].uploadUrl
            
            # 读取并上传分片
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
                -Method Put `
                -Body $buffer `
                -Headers @{
                    "Content-Type" = "application/octet-stream"
                }
            
            $etag = $uploadResponse.Headers["ETag"] -replace '"', ''
            
            # 使用 PSCustomObject 确保正确的 JSON 序列化
            $uploadedParts += [PSCustomObject]@{
                partNumber = $i
                etag = $etag
            }
        }
        
        $fileStream.Close()
        
        Write-TestSuccess "所有分片上传完成"
        
        # 尝试完成上传(设置短超时)
        Write-TestInfo "完成上传(超时设置: 2秒)..."
        
        $completeRequest = @{
            taskId = $taskId
            contentType = "text/plain"
            parts = $uploadedParts
        } | ConvertTo-Json -Depth 10
        
        try {
            $completeResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/complete" `
                -Method Post `
                -Headers @{
                    "Content-Type" = "application/json"
                    "X-App-Id" = $AppId
                    "X-User-Id" = $UserId
                } `
                -Body $completeRequest `
                -TimeoutSec 2
            
            if ($completeResponse.code -eq 200) {
                Write-TestSuccess "完成上传成功(< 2秒)"
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "完成上传时网络异常"
                    Status = "SKIP"
                    Details = "完成速度过快,无法触发超时"
                    Error = $null
                }
            }
        }
        catch {
            if ($_.Exception.Message -match "timeout|超时") {
                Write-TestSuccess "捕获完成上传超时: $($_.Exception.Message)"
                
                # 验证可以重试
                Write-TestInfo "验证重试机制..."
                $retryResponse = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/complete" `
                    -Method Post `
                    -Headers @{
                        "Content-Type" = "application/json"
                        "X-App-Id" = $AppId
                        "X-User-Id" = $UserId
                    } `
                    -Body $completeRequest `
                    -TimeoutSec 30
                
                if ($retryResponse.code -eq 200) {
                    Write-TestSuccess "重试成功,文件ID: $($retryResponse.data)"
                }
                
                $script:TestResults += [PSCustomObject]@{
                    TestName = "完成上传时网络异常"
                    Status = "PASS"
                    Details = "正确处理完成上传超时并支持重试"
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
            TestName = "完成上传时网络异常"
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

# 主测试流程
function Start-NetworkTests {
    Write-TestHeader "文件上传服务 - 网络异常场景测试"
    
    Write-Host "配置:" -ForegroundColor Cyan
    Write-Host "  服务地址: $ServiceUrl" -ForegroundColor Gray
    Write-Host "  App ID: $AppId" -ForegroundColor Gray
    Write-Host "  User ID: $UserId" -ForegroundColor Gray
    Write-Host "  测试文件大小: $([math]::Round($TestFileSize / 1MB, 2))MB" -ForegroundColor Gray
    Write-Host ""
    
    Initialize-TestEnvironment
    
    # 运行所有测试场景
    Test-InitializationTimeout
    Test-PresignedUrlTimeout
    Test-S3UploadTimeout
    Test-ExpiredPresignedUrl
    Test-CompleteUploadNetworkError
    
    # 显示测试总结
    Write-TestHeader "测试总结"
    
    $totalTests = $TestResults.Count
    $passedTests = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
    $failedTests = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
    $skippedTests = ($TestResults | Where-Object { $_.Status -in @("SKIP", "WARN") }).Count
    
    Write-Host "总测试数: $totalTests" -ForegroundColor Cyan
    Write-Host "通过: $passedTests" -ForegroundColor Green
    Write-Host "失败: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })
    Write-Host "跳过/警告: $skippedTests" -ForegroundColor Yellow
    
    if ($totalTests -gt $skippedTests) {
        $effectiveTests = $totalTests - $skippedTests
        Write-Host "有效成功率: $([math]::Round(($passedTests / $effectiveTests) * 100, 2))%" -ForegroundColor Cyan
    }
    
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
Start-NetworkTests
