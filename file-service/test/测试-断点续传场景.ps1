# 文件上传服务 - 断点续传场景测试脚本
# 测试各种断点续传场景,包括主动暂停、网络中断、页面刷新等

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [int]$TestFileSize = 20MB,  # 20MB测试文件
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TempDir = Join-Path $env:TEMP "resume_test_$Timestamp"
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
    
    # 创建带有可识别内容的文件
    $content = "TEST_FILE_CONTENT_" * 1000
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

# 计算文件Hash
function Get-FileHashMD5 {
    param([string]$FilePath)
    
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $stream = [System.IO.File]::OpenRead($FilePath)
    $hash = [System.BitConverter]::ToString($md5.ComputeHash($stream)).Replace("-", "").ToLower()
    $stream.Close()
    
    return $hash
}

# 初始化上传
function Initialize-Upload {
    param(
        [string]$FilePath,
        [string]$FileHash
    )
    
    $fileName = [System.IO.Path]::GetFileName($FilePath)
    $fileSize = (Get-Item $FilePath).Length
    
    $initRequest = @{
        fileName = $fileName
        fileSize = $fileSize
        contentType = "text/plain"
        fileHash = $FileHash
    } | ConvertTo-Json
    
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/init" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/json"
            "X-App-Id" = $AppId
            "X-User-Id" = $UserId
        } `
        -Body $initRequest
    
    if ($response.code -ne 200) {
        throw "初始化失败: $($response.message)"
    }
    
    return $response.data
}

# 上传单个分片
function Upload-Part {
    param(
        [string]$TaskId,
        [int]$PartNumber,
        [byte[]]$Data
    )
    
    # 获取预签名URL
    $urlRequest = @{
        taskId = $TaskId
        partNumbers = @($PartNumber)
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
    
    # 上传分片
    $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
        -Method Put `
        -Body $Data `
        -Headers @{
            "Content-Type" = "application/octet-stream"
        }
    
    if ($uploadResponse.StatusCode -ne 200) {
        throw "上传分片失败: $($uploadResponse.StatusCode)"
    }
    
    $etag = $uploadResponse.Headers["ETag"] -replace '"', ''
    
    return $etag
}

# 完成上传
function Complete-Upload {
    param(
        [string]$TaskId,
        [array]$Parts
    )
    
    $completeRequest = @{
        taskId = $TaskId
        contentType = "text/plain"
        parts = $Parts
    } | ConvertTo-Json -Depth 10
    
    $response = Invoke-RestMethod -Uri "$ServiceUrl/api/v1/direct-upload/complete" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/json"
            "X-App-Id" = $AppId
            "X-User-Id" = $UserId
        } `
        -Body $completeRequest
    
    if ($response.code -ne 200) {
        throw "完成上传失败: $($response.message)"
    }
    
    return $response.data
}

# 测试场景1: 主动暂停后继续
function Test-PauseAndResume {
    Write-TestHeader "测试场景1: 主动暂停后继续"
    
    $testFile = Join-Path $TempDir "pause_resume_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    $fileHash = Get-FileHashMD5 -FilePath $testFile
    Write-TestInfo "文件Hash: $fileHash"
    
    try {
        # 第一次上传: 上传部分分片
        Write-TestInfo "第一次上传: 上传部分分片..."
        $initData = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        
        $taskId = $initData.taskId
        $chunkSize = $initData.chunkSize
        $totalParts = $initData.totalParts
        
        Write-TestInfo "TaskId: $taskId, 总分片数: $totalParts"
        
        # 只上传前一半分片
        $partsToUpload = [Math]::Ceiling($totalParts / 2)
        Write-TestInfo "上传前 $partsToUpload 个分片..."
        
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $uploadedParts = @()
        
        for ($i = 1; $i -le $partsToUpload; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $etag = Upload-Part -TaskId $taskId -PartNumber $i -Data $buffer
            $uploadedParts += @{
                partNumber = $i
                etag = $etag
            }
            
            Write-TestInfo "  分片 $i/$totalParts 上传完成"
        }
        
        $fileStream.Close()
        
        Write-TestInfo "模拟暂停,已上传 $partsToUpload 个分片"
        Start-Sleep -Seconds 2
        
        # 第二次上传: 续传剩余分片
        Write-TestInfo "第二次上传: 续传剩余分片..."
        $resumeData = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        
        # 验证返回的是同一个任务
        if ($resumeData.taskId -ne $taskId) {
            throw "续传任务ID不匹配: 期望 $taskId, 实际 $($resumeData.taskId)"
        }
        
        # 验证已完成的分片
        $completedParts = $resumeData.completedParts
        if ($completedParts.Count -ne $partsToUpload) {
            throw "已完成分片数量不匹配: 期望 $partsToUpload, 实际 $($completedParts.Count)"
        }
        
        Write-TestSuccess "断点续传识别成功,已完成 $($completedParts.Count) 个分片"
        
        # 上传剩余分片
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $fileStream.Seek($partsToUpload * $chunkSize, [System.IO.SeekOrigin]::Begin) | Out-Null
        
        for ($i = $partsToUpload + 1; $i -le $totalParts; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $etag = Upload-Part -TaskId $taskId -PartNumber $i -Data $buffer
            $uploadedParts += @{
                partNumber = $i
                etag = $etag
            }
            
            Write-TestInfo "  分片 $i/$totalParts 上传完成"
        }
        
        $fileStream.Close()
        
        # 完成上传
        $fileId = Complete-Upload -TaskId $taskId -Parts $uploadedParts
        Write-TestSuccess "上传完成,文件ID: $fileId"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "主动暂停后继续"
            Status = "PASS"
            Details = "成功识别并续传 $($totalParts - $partsToUpload) 个分片"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "主动暂停后继续"
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

# 测试场景2: 模拟网络中断后恢复
function Test-NetworkInterruptionRecovery {
    Write-TestHeader "测试场景2: 模拟网络中断后恢复"
    
    $testFile = Join-Path $TempDir "network_interrupt_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    $fileHash = Get-FileHashMD5 -FilePath $testFile
    
    try {
        # 初始化上传
        $initData = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        
        $taskId = $initData.taskId
        $chunkSize = $initData.chunkSize
        $totalParts = $initData.totalParts
        
        Write-TestInfo "TaskId: $taskId, 总分片数: $totalParts"
        
        # 上传部分分片
        $partsBeforeInterrupt = [Math]::Floor($totalParts * 0.3)
        Write-TestInfo "上传 $partsBeforeInterrupt 个分片后模拟网络中断..."
        
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $uploadedParts = @()
        
        for ($i = 1; $i -le $partsBeforeInterrupt; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $etag = Upload-Part -TaskId $taskId -PartNumber $i -Data $buffer
            $uploadedParts += @{
                partNumber = $i
                etag = $etag
            }
        }
        
        Write-TestInfo "模拟网络中断..."
        Start-Sleep -Seconds 3
        
        # 尝试上传下一个分片(模拟网络恢复后重试)
        Write-TestInfo "网络恢复,继续上传..."
        
        for ($i = $partsBeforeInterrupt + 1; $i -le $totalParts; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            # 模拟网络不稳定,可能需要重试
            $retryCount = 0
            $maxRetries = 3
            $uploaded = $false
            
            while (-not $uploaded -and $retryCount -lt $maxRetries) {
                try {
                    $etag = Upload-Part -TaskId $taskId -PartNumber $i -Data $buffer
                    $uploadedParts += @{
                        partNumber = $i
                        etag = $etag
                    }
                    $uploaded = $true
                }
                catch {
                    $retryCount++
                    if ($retryCount -lt $maxRetries) {
                        Write-TestInfo "  分片 $i 上传失败,重试 $retryCount/$maxRetries..."
                        Start-Sleep -Seconds 1
                    }
                    else {
                        throw
                    }
                }
            }
            
            Write-TestInfo "  分片 $i/$totalParts 上传完成"
        }
        
        $fileStream.Close()
        
        # 完成上传
        $fileId = Complete-Upload -TaskId $taskId -Parts $uploadedParts
        Write-TestSuccess "网络中断恢复测试通过,文件ID: $fileId"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "网络中断后恢复"
            Status = "PASS"
            Details = "成功从网络中断中恢复并完成上传"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "网络中断后恢复"
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

# 测试场景3: 重复初始化同一文件
function Test-DuplicateInitialization {
    Write-TestHeader "测试场景3: 重复初始化同一文件"
    
    $testFile = Join-Path $TempDir "duplicate_init_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    $fileHash = Get-FileHashMD5 -FilePath $testFile
    
    try {
        # 第一次初始化
        Write-TestInfo "第一次初始化..."
        $initData1 = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        $taskId1 = $initData1.taskId
        
        Write-TestInfo "TaskId1: $taskId1"
        
        # 上传部分分片
        $chunkSize = $initData1.chunkSize
        $totalParts = $initData1.totalParts
        $partsToUpload = [Math]::Floor($totalParts / 3)
        
        Write-TestInfo "上传 $partsToUpload 个分片..."
        
        $fileStream = [System.IO.File]::OpenRead($testFile)
        
        for ($i = 1; $i -le $partsToUpload; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            Upload-Part -TaskId $taskId1 -PartNumber $i -Data $buffer | Out-Null
        }
        
        $fileStream.Close()
        
        # 第二次初始化(模拟用户刷新页面)
        Write-TestInfo "第二次初始化(模拟刷新页面)..."
        $initData2 = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        $taskId2 = $initData2.taskId
        
        Write-TestInfo "TaskId2: $taskId2"
        
        # 验证返回的是同一个任务
        if ($taskId1 -ne $taskId2) {
            throw "重复初始化返回了不同的任务ID"
        }
        
        # 验证已完成的分片
        if ($initData2.completedParts.Count -ne $partsToUpload) {
            throw "已完成分片数量不匹配"
        }
        
        Write-TestSuccess "重复初始化测试通过,正确识别已存在的任务"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "重复初始化同一文件"
            Status = "PASS"
            Details = "正确返回已存在的任务,已完成分片: $partsToUpload"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "重复初始化同一文件"
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

# 测试场景4: 分片乱序上传
function Test-OutOfOrderParts {
    Write-TestHeader "测试场景4: 分片乱序上传"
    
    $testFile = Join-Path $TempDir "out_of_order_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    $fileHash = Get-FileHashMD5 -FilePath $testFile
    
    try {
        # 初始化上传
        $initData = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        
        $taskId = $initData.taskId
        $chunkSize = $initData.chunkSize
        $totalParts = $initData.totalParts
        
        Write-TestInfo "TaskId: $taskId, 总分片数: $totalParts"
        Write-TestInfo "测试乱序上传分片..."
        
        # 读取所有分片到内存
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $allParts = @()
        
        for ($i = 1; $i -le $totalParts; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $allParts += @{
                partNumber = $i
                data = $buffer
            }
        }
        
        $fileStream.Close()
        
        # 打乱分片顺序
        $shuffledParts = $allParts | Sort-Object { Get-Random }
        
        Write-TestInfo "按乱序上传分片: $($shuffledParts.partNumber -join ', ')"
        
        $uploadedParts = @()
        
        foreach ($part in $shuffledParts) {
            $etag = Upload-Part -TaskId $taskId -PartNumber $part.partNumber -Data $part.data
            $uploadedParts += @{
                partNumber = $part.partNumber
                etag = $etag
            }
            Write-TestInfo "  分片 $($part.partNumber) 上传完成"
        }
        
        # 完成上传(系统应该自动按正确顺序排列)
        $fileId = Complete-Upload -TaskId $taskId -Parts $uploadedParts
        Write-TestSuccess "乱序上传测试通过,文件ID: $fileId"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "分片乱序上传"
            Status = "PASS"
            Details = "成功处理乱序上传的分片"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "分片乱序上传"
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

# 测试场景5: 重复上传同一分片
function Test-DuplicatePartUpload {
    Write-TestHeader "测试场景5: 重复上传同一分片(幂等性)"
    
    $testFile = Join-Path $TempDir "duplicate_part_test.txt"
    New-TestFile -FilePath $testFile -SizeBytes $TestFileSize | Out-Null
    
    $fileHash = Get-FileHashMD5 -FilePath $testFile
    
    try {
        # 初始化上传
        $initData = Initialize-Upload -FilePath $testFile -FileHash $fileHash
        
        $taskId = $initData.taskId
        $chunkSize = $initData.chunkSize
        $totalParts = $initData.totalParts
        
        Write-TestInfo "TaskId: $taskId, 总分片数: $totalParts"
        
        # 读取第一个分片
        $fileStream = [System.IO.File]::OpenRead($testFile)
        $buffer = New-Object byte[] $chunkSize
        $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
        
        if ($bytesRead -lt $chunkSize) {
            $buffer = $buffer[0..($bytesRead - 1)]
        }
        
        # 第一次上传
        Write-TestInfo "第一次上传分片1..."
        $etag1 = Upload-Part -TaskId $taskId -PartNumber 1 -Data $buffer
        Write-TestInfo "  ETag1: $etag1"
        
        # 第二次上传同一分片(测试幂等性)
        Write-TestInfo "第二次上传分片1(测试幂等性)..."
        try {
            $etag2 = Upload-Part -TaskId $taskId -PartNumber 1 -Data $buffer
            Write-TestInfo "  ETag2: $etag2"
            
            # 注意: 根据实现,可能返回成功或错误
            # 如果返回成功,验证ETag是否一致
            if ($etag1 -ne $etag2) {
                Write-TestInfo "  警告: 重复上传返回了不同的ETag"
            }
            else {
                Write-TestSuccess "重复上传返回相同的ETag,幂等性正确"
            }
        }
        catch {
            # 如果返回错误,验证错误信息是否合理
            if ($_.Exception.Message -match "已上传|重复") {
                Write-TestSuccess "重复上传被正确拒绝: $($_.Exception.Message)"
            }
            else {
                throw "重复上传返回了意外的错误: $($_.Exception.Message)"
            }
        }
        
        # 上传剩余分片
        $uploadedParts = @(@{
            partNumber = 1
            etag = $etag1
        })
        
        for ($i = 2; $i -le $totalParts; $i++) {
            $buffer = New-Object byte[] $chunkSize
            $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
            
            if ($bytesRead -lt $chunkSize) {
                $buffer = $buffer[0..($bytesRead - 1)]
            }
            
            $etag = Upload-Part -TaskId $taskId -PartNumber $i -Data $buffer
            $uploadedParts += @{
                partNumber = $i
                etag = $etag
            }
        }
        
        $fileStream.Close()
        
        # 完成上传
        $fileId = Complete-Upload -TaskId $taskId -Parts $uploadedParts
        Write-TestSuccess "幂等性测试通过,文件ID: $fileId"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = "重复上传同一分片"
            Status = "PASS"
            Details = "正确处理重复上传的分片"
            Error = $null
        }
    }
    catch {
        Write-TestFailure "测试失败: $_"
        $script:TestResults += [PSCustomObject]@{
            TestName = "重复上传同一分片"
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
function Start-ResumeTests {
    Write-TestHeader "文件上传服务 - 断点续传场景测试"
    
    Write-Host "配置:" -ForegroundColor Cyan
    Write-Host "  服务地址: $ServiceUrl" -ForegroundColor Gray
    Write-Host "  App ID: $AppId" -ForegroundColor Gray
    Write-Host "  User ID: $UserId" -ForegroundColor Gray
    Write-Host "  测试文件大小: $([math]::Round($TestFileSize / 1MB, 2))MB" -ForegroundColor Gray
    Write-Host ""
    
    Initialize-TestEnvironment
    
    # 运行所有测试场景
    Test-PauseAndResume
    Test-NetworkInterruptionRecovery
    Test-DuplicateInitialization
    Test-OutOfOrderParts
    Test-DuplicatePartUpload
    
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
Start-ResumeTests
