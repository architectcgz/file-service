# File Service - Large File Upload and Stress Test Script
# Tests large file uploads (10MB, 50MB, 100MB) and concurrent upload scenarios
# Generates performance metrics and detailed reports

param(
    [string]$ServiceUrl = "http://localhost:8089",
    [string]$AppId = "blog",
    [string]$TestToken = "",
    [int]$ConcurrentUsers = 10,
    [switch]$SkipCleanup,
    [switch]$GenerateReport
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TempDir = Join-Path $env:TEMP "file_service_load_test_$Timestamp"
$ReportDir = Join-Path $ScriptDir "reports"

# Test configuration
$TestConfig = @{
    SmallFileSize = 1MB
    MediumFileSize = 10MB
    LargeFileSize = 50MB
    XLargeFileSize = 100MB
    ConcurrentTests = @(5, 10, 20, 50)
    TimeoutSeconds = 300
    MultipartThreshold = 10MB  # 超过此大小使用分片上传
    ChunkSize = 5MB            # 分片大小
}

# Results storage
$TestResults = @()
$PerformanceMetrics = @()

# Color output functions
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

# Create test directories
function Initialize-TestEnvironment {
    Write-TestInfo "Initializing test environment..."
    
    if (-not (Test-Path $TempDir)) {
        New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
        Write-TestInfo "Created temp directory: $TempDir"
    }
    
    if ($GenerateReport -and -not (Test-Path $ReportDir)) {
        New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
        Write-TestInfo "Created report directory: $ReportDir"
    }
}

# Generate test file of specified size
function New-TestFile {
    param(
        [string]$FilePath,
        [long]$SizeBytes,
        [string]$FileType = "image"
    )
    
    Write-TestInfo "Generating test file: $([math]::Round($SizeBytes / 1MB, 2))MB"
    
    if ($FileType -eq "image") {
        # Create a valid JPEG file with random data
        $JpegHeader = [byte[]]@(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46)
        $JpegFooter = [byte[]]@(0xFF, 0xD9)
        
        $DataSize = $SizeBytes - $JpegHeader.Length - $JpegFooter.Length
        $RandomData = New-Object byte[] $DataSize
        $Random = New-Object System.Random
        $Random.NextBytes($RandomData)
        
        $FileBytes = $JpegHeader + $RandomData + $JpegFooter
        [System.IO.File]::WriteAllBytes($FilePath, $FileBytes)
    }
    elseif ($FileType -eq "pdf") {
        # Create a minimal PDF with padding
        $PdfHeader = @"
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
endobj
"@
        $PdfFooter = @"
xref
0 4
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
trailer
<< /Size 4 /Root 1 0 R >>
startxref
196
%%EOF
"@
        $HeaderBytes = [System.Text.Encoding]::UTF8.GetBytes($PdfHeader)
        $FooterBytes = [System.Text.Encoding]::UTF8.GetBytes($PdfFooter)
        
        $PaddingSize = $SizeBytes - $HeaderBytes.Length - $FooterBytes.Length
        if ($PaddingSize -gt 0) {
            $Padding = New-Object byte[] $PaddingSize
            $FileBytes = $HeaderBytes + $Padding + $FooterBytes
        }
        else {
            $FileBytes = $HeaderBytes + $FooterBytes
        }
        
        [System.IO.File]::WriteAllBytes($FilePath, $FileBytes)
    }
    
    $ActualSize = (Get-Item $FilePath).Length
    Write-TestInfo "File created: $([math]::Round($ActualSize / 1MB, 2))MB"
    return $FilePath
}

# Upload file using multipart form data (for small files)
function Invoke-FileUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$AppId,
        [string]$Token,
        [int]$TimeoutSeconds = 300
    )
    
    $StartTime = Get-Date
    $Result = @{
        Success = $false
        StatusCode = 0
        FileId = $null
        Url = $null
        Duration = 0
        Throughput = 0
        Error = $null
    }
    
    try {
        $FileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $FileName = [System.IO.Path]::GetFileName($FilePath)
        $FileSize = $FileBytes.Length
        $Extension = [System.IO.Path]::GetExtension($FilePath).ToLower()
        
        # Determine content type and endpoint
        # 使用 /api/v1/upload/file 端点避免图片处理问题
        $ContentType = switch ($Extension) {
            ".jpg"  { "image/jpeg" }
            ".jpeg" { "image/jpeg" }
            ".png"  { "image/png" }
            ".pdf"  { "application/pdf" }
            default { "application/octet-stream" }
        }
        
        # 统一使用文件上传端点,避免图片处理导致的格式问题
        $Endpoint = "/api/v1/upload/file"
        $UploadUrl = "$Url$Endpoint"
        
        # Create multipart form data
        $Boundary = [System.Guid]::NewGuid().ToString()
        $LF = "`r`n"
        
        $BodyLines = @(
            "--$Boundary",
            "Content-Disposition: form-data; name=`"file`"; filename=`"$FileName`"",
            "Content-Type: $ContentType",
            ""
        )
        
        $HeaderBytes = [System.Text.Encoding]::UTF8.GetBytes(($BodyLines -join $LF) + $LF)
        $FooterBytes = [System.Text.Encoding]::UTF8.GetBytes("$LF--$Boundary--$LF")
        
        $BodyBytes = New-Object byte[] ($HeaderBytes.Length + $FileBytes.Length + $FooterBytes.Length)
        [System.Buffer]::BlockCopy($HeaderBytes, 0, $BodyBytes, 0, $HeaderBytes.Length)
        [System.Buffer]::BlockCopy($FileBytes, 0, $BodyBytes, $HeaderBytes.Length, $FileBytes.Length)
        [System.Buffer]::BlockCopy($FooterBytes, 0, $BodyBytes, $HeaderBytes.Length + $FileBytes.Length, $FooterBytes.Length)
        
        $Headers = @{
            "Content-Type" = "multipart/form-data; boundary=$Boundary"
            "X-App-Id" = $AppId
            "X-User-Id" = "1"  # 测试用户 ID
        }
        
        if ($Token) {
            $Headers["Authorization"] = "Bearer $Token"
        }
        
        $Response = Invoke-WebRequest -Uri $UploadUrl -Method POST -Body $BodyBytes -Headers $Headers -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        
        $Duration = ((Get-Date) - $StartTime).TotalSeconds
        $Throughput = [math]::Round(($FileSize / 1MB) / $Duration, 2)
        
        $ResponseData = $Response.Content | ConvertFrom-Json
        
        if ($ResponseData.code -eq 200 -and $ResponseData.data) {
            $Result.Success = $true
            $Result.StatusCode = $Response.StatusCode
            $Result.FileId = $ResponseData.data.fileId
            $Result.Url = $ResponseData.data.url
            $Result.Duration = [math]::Round($Duration, 2)
            $Result.Throughput = $Throughput
        }
        else {
            $Result.Error = $ResponseData.message
        }
    }
    catch {
        $Result.Error = $_.Exception.Message
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
        }
    }
    
    return $Result
}

# Upload large file using multipart upload API with parallel chunk uploads
function Invoke-MultipartUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$AppId,
        [string]$Token,
        [long]$ChunkSize = 5MB,
        [int]$TimeoutSeconds = 300,
        [int]$MaxParallelUploads = 3
    )
    
    $StartTime = Get-Date
    $Result = @{
        Success = $false
        StatusCode = 0
        FileId = $null
        Url = $null
        Duration = 0
        Throughput = 0
        Error = $null
    }
    
    try {
        $FileName = [System.IO.Path]::GetFileName($FilePath)
        $FileSize = (Get-Item $FilePath).Length
        $Extension = [System.IO.Path]::GetExtension($FilePath).ToLower()
        
        # Determine content type
        $ContentType = switch ($Extension) {
            ".jpg"  { "image/jpeg" }
            ".jpeg" { "image/jpeg" }
            ".png"  { "image/png" }
            ".pdf"  { "application/pdf" }
            default { "application/octet-stream" }
        }
        
        $Headers = @{
            "Content-Type" = "application/json"
            "X-App-Id" = $AppId
            "X-User-Id" = "1"  # 测试用户 ID
        }
        
        if ($Token) {
            $Headers["Authorization"] = "Bearer $Token"
        }
        
        # Step 1: 初始化分片上传
        $InitBody = @{
            fileName = $FileName
            fileSize = $FileSize
            contentType = $ContentType
        } | ConvertTo-Json
        
        $InitResponse = Invoke-WebRequest -Uri "$Url/api/v1/multipart/init" -Method POST -Body $InitBody -Headers $Headers -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        $InitData = $InitResponse.Content | ConvertFrom-Json
        
        if ($InitData.code -ne 200) {
            $Result.Error = "初始化失败: $($InitData.message)"
            return $Result
        }
        
        $TaskId = $InitData.data.taskId
        $TotalParts = $InitData.data.totalParts
        
        Write-TestInfo "  TaskId: $TaskId, TotalParts: $TotalParts, 并行度: $MaxParallelUploads"
        
        # Step 2: 并行上传分片（使用 Runspace Pool）
        $FileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        
        # 创建 Runspace Pool
        $RunspacePool = [runspacefactory]::CreateRunspacePool(1, $MaxParallelUploads)
        $RunspacePool.Open()
        
        $UploadTasks = @()
        $PartNumber = 1
        $Offset = 0
        
        # 创建所有上传任务
        while ($Offset -lt $FileSize) {
            $BytesToRead = [Math]::Min($ChunkSize, $FileSize - $Offset)
            $PartData = $FileBytes[$Offset..($Offset + $BytesToRead - 1)]
            $CurrentPartNumber = $PartNumber
            
            # 创建 PowerShell 实例
            $PowerShell = [powershell]::Create()
            $PowerShell.RunspacePool = $RunspacePool
            
            # 添加脚本
            [void]$PowerShell.AddScript({
                param($Url, $TaskId, $PartNumber, $PartData, $AppId, $TimeoutSeconds)
                
                try {
                    $PartHeaders = @{
                        "Content-Type" = "application/octet-stream"
                        "X-App-Id" = $AppId
                        "X-User-Id" = "1"
                    }
                    
                    $PartResponse = Invoke-WebRequest -Uri "$Url/api/v1/multipart/$TaskId/parts/$PartNumber" -Method PUT -Body $PartData -Headers $PartHeaders -TimeoutSec $TimeoutSeconds -ErrorAction Stop
                    $ResponseData = $PartResponse.Content | ConvertFrom-Json
                    
                    return @{
                        Success = ($ResponseData.code -eq 200)
                        PartNumber = $PartNumber
                        Error = if ($ResponseData.code -ne 200) { $ResponseData.message } else { $null }
                    }
                }
                catch {
                    return @{
                        Success = $false
                        PartNumber = $PartNumber
                        Error = $_.Exception.Message
                    }
                }
            }).AddArgument($Url).AddArgument($TaskId).AddArgument($CurrentPartNumber).AddArgument($PartData).AddArgument($AppId).AddArgument($TimeoutSeconds)
            
            # 开始异步执行
            $AsyncResult = $PowerShell.BeginInvoke()
            
            $UploadTasks += @{
                PowerShell = $PowerShell
                AsyncResult = $AsyncResult
                PartNumber = $CurrentPartNumber
            }
            
            $PartNumber++
            $Offset += $BytesToRead
        }
        
        # 等待所有任务完成并收集结果
        Write-TestInfo "  等待所有分片上传完成..."
        $UploadedParts = 0
        $FailedParts = @()
        
        foreach ($Task in $UploadTasks) {
            try {
                $TaskResult = $Task.PowerShell.EndInvoke($Task.AsyncResult)
                
                if ($TaskResult.Success) {
                    $UploadedParts++
                }
                else {
                    $FailedParts += @{
                        PartNumber = $TaskResult.PartNumber
                        Error = $TaskResult.Error
                    }
                }
                
                # 显示进度
                if ($UploadedParts % 2 -eq 0 -or $UploadedParts -eq $TotalParts) {
                    $Progress = [math]::Round(($UploadedParts / $TotalParts) * 100, 1)
                    Write-TestInfo "  上传进度: $Progress% ($UploadedParts/$TotalParts)"
                }
            }
            finally {
                $Task.PowerShell.Dispose()
            }
        }
        
        # 清理 Runspace Pool
        $RunspacePool.Close()
        $RunspacePool.Dispose()
        
        # 检查是否有失败的分片
        if ($FailedParts.Count -gt 0) {
            $ErrorMsg = "分片上传失败: " + ($FailedParts | ForEach-Object { "Part $($_.PartNumber): $($_.Error)" }) -join "; "
            $Result.Error = $ErrorMsg
            return $Result
        }
        
        # Step 3: 完成上传
        $CompleteResponse = Invoke-WebRequest -Uri "$Url/api/v1/multipart/$TaskId/complete" -Method POST -Headers $Headers -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        $CompleteData = $CompleteResponse.Content | ConvertFrom-Json
        
        if ($CompleteData.code -ne 200) {
            $Result.Error = "完成上传失败: $($CompleteData.message)"
            return $Result
        }
        
        $Duration = ((Get-Date) - $StartTime).TotalSeconds
        $Throughput = [math]::Round(($FileSize / 1MB) / $Duration, 2)
        
        $Result.Success = $true
        $Result.StatusCode = 200
        $Result.FileId = $CompleteData.data.fileId
        $Result.Url = $CompleteData.data.url
        $Result.Duration = [math]::Round($Duration, 2)
        $Result.Throughput = $Throughput
    }
    catch {
        $Result.Error = $_.Exception.Message
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
        }
    }
    
    return $Result
}

# Test single file upload
function Test-SingleFileUpload {
    param(
        [string]$TestName,
        [long]$FileSize,
        [string]$FileType = "image"
    )
    
    Write-TestInfo "Running test: $TestName"
    Write-TestInfo "File size: $([math]::Round($FileSize / 1MB, 2))MB"
    
    $Extension = if ($FileType -eq "image") { ".jpg" } else { ".pdf" }
    $TestFile = Join-Path $TempDir "test_$([math]::Round($FileSize / 1MB))MB$Extension"
    
    try {
        New-TestFile -FilePath $TestFile -SizeBytes $FileSize -FileType $FileType
        
        # 根据文件大小选择上传方式
        if ($FileSize -gt $TestConfig.MultipartThreshold) {
            Write-TestInfo "使用分片上传 (文件大小: $([math]::Round($FileSize / 1MB, 2))MB > $([math]::Round($TestConfig.MultipartThreshold / 1MB, 2))MB)"
            $Result = Invoke-MultipartUpload -Url $ServiceUrl -FilePath $TestFile -AppId $AppId -Token $TestToken -ChunkSize $TestConfig.ChunkSize -TimeoutSeconds $TestConfig.TimeoutSeconds
        }
        else {
            Write-TestInfo "使用普通上传 (文件大小: $([math]::Round($FileSize / 1MB, 2))MB <= $([math]::Round($TestConfig.MultipartThreshold / 1MB, 2))MB)"
            $Result = Invoke-FileUpload -Url $ServiceUrl -FilePath $TestFile -AppId $AppId -Token $TestToken -TimeoutSeconds $TestConfig.TimeoutSeconds
        }
        
        if ($Result.Success) {
            Write-TestSuccess "$TestName completed in $($Result.Duration)s ($($Result.Throughput) MB/s)"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = $TestName
                FileSize = "$([math]::Round($FileSize / 1MB, 2))MB"
                Status = "PASS"
                Duration = "$($Result.Duration)s"
                Throughput = "$($Result.Throughput) MB/s"
                FileId = $Result.FileId
                Error = $null
            }
            
            $script:PerformanceMetrics += [PSCustomObject]@{
                TestName = $TestName
                FileSizeMB = [math]::Round($FileSize / 1MB, 2)
                DurationSeconds = $Result.Duration
                ThroughputMBps = $Result.Throughput
                Success = $true
            }
        }
        else {
            Write-TestFailure "$TestName failed: $($Result.Error)"
            
            $script:TestResults += [PSCustomObject]@{
                TestName = $TestName
                FileSize = "$([math]::Round($FileSize / 1MB, 2))MB"
                Status = "FAIL"
                Duration = "-"
                Throughput = "-"
                FileId = $null
                Error = $Result.Error
            }
        }
    }
    catch {
        Write-TestFailure "$TestName exception: $($_.Exception.Message)"
        
        $script:TestResults += [PSCustomObject]@{
            TestName = $TestName
            FileSize = "$([math]::Round($FileSize / 1MB, 2))MB"
            Status = "ERROR"
            Duration = "-"
            Throughput = "-"
            FileId = $null
            Error = $_.Exception.Message
        }
    }
    finally {
        if (Test-Path $TestFile) {
            Remove-Item $TestFile -Force
        }
    }
}

# Test concurrent uploads
function Test-ConcurrentUploads {
    param(
        [int]$UserCount,
        [long]$FileSize
    )
    
    Write-TestInfo "Running concurrent upload test: $UserCount users, $([math]::Round($FileSize / 1MB, 2))MB files"
    
    $Jobs = @()
    $StartTime = Get-Date
    
    # Create test files
    $TestFiles = @()
    for ($i = 1; $i -le $UserCount; $i++) {
        $TestFile = Join-Path $TempDir "concurrent_${i}_$([math]::Round($FileSize / 1MB))MB.jpg"
        New-TestFile -FilePath $TestFile -SizeBytes $FileSize -FileType "image" | Out-Null
        $TestFiles += $TestFile
    }
    
    Write-TestInfo "Starting $UserCount concurrent uploads..."
    
    # Start concurrent uploads
    foreach ($TestFile in $TestFiles) {
        $Job = Start-Job -ScriptBlock {
            param($Url, $FilePath, $AppId, $Token, $Timeout)
            
            # Re-define the upload function in job context
            function Invoke-FileUpload {
                param($Url, $FilePath, $AppId, $Token, $TimeoutSeconds)
                
                $StartTime = Get-Date
                $Result = @{ Success = $false; Duration = 0; Error = $null }
                
                try {
                    $FileBytes = [System.IO.File]::ReadAllBytes($FilePath)
                    $FileName = [System.IO.Path]::GetFileName($FilePath)
                    $FileSize = $FileBytes.Length
                    
                    $Boundary = [System.Guid]::NewGuid().ToString()
                    $LF = "`r`n"
                    
                    $BodyLines = @(
                        "--$Boundary",
                        "Content-Disposition: form-data; name=`"file`"; filename=`"$FileName`"",
                        "Content-Type: image/jpeg",
                        ""
                    )
                    
                    $HeaderBytes = [System.Text.Encoding]::UTF8.GetBytes(($BodyLines -join $LF) + $LF)
                    $FooterBytes = [System.Text.Encoding]::UTF8.GetBytes("$LF--$Boundary--$LF")
                    
                    $BodyBytes = New-Object byte[] ($HeaderBytes.Length + $FileBytes.Length + $FooterBytes.Length)
                    [System.Buffer]::BlockCopy($HeaderBytes, 0, $BodyBytes, 0, $HeaderBytes.Length)
                    [System.Buffer]::BlockCopy($FileBytes, 0, $BodyBytes, $HeaderBytes.Length, $FileBytes.Length)
                    [System.Buffer]::BlockCopy($FooterBytes, 0, $BodyBytes, $HeaderBytes.Length + $FileBytes.Length, $FooterBytes.Length)
                    
                    $Headers = @{
                        "Content-Type" = "multipart/form-data; boundary=$Boundary"
                        "X-App-Id" = $AppId
                        "X-User-Id" = "1"  # 测试用户 ID
                    }
                    
                    if ($Token) {
                        $Headers["Authorization"] = "Bearer $Token"
                    }
                    
                    $Response = Invoke-WebRequest -Uri "$Url/api/v1/upload/file" -Method POST -Body $BodyBytes -Headers $Headers -TimeoutSec $TimeoutSeconds -ErrorAction Stop
                    
                    $Duration = ((Get-Date) - $StartTime).TotalSeconds
                    $ResponseData = $Response.Content | ConvertFrom-Json
                    
                    if ($ResponseData.code -eq 200) {
                        $Result.Success = $true
                        $Result.Duration = [math]::Round($Duration, 2)
                    }
                    else {
                        $Result.Error = $ResponseData.message
                    }
                }
                catch {
                    $Result.Error = $_.Exception.Message
                }
                
                return $Result
            }
            
            Invoke-FileUpload -Url $Url -FilePath $FilePath -AppId $AppId -Token $Token -TimeoutSeconds $Timeout
        } -ArgumentList $ServiceUrl, $TestFile, $AppId, $TestToken, $TestConfig.TimeoutSeconds
        
        $Jobs += $Job
    }
    
    # Wait for all jobs to complete
    Write-TestInfo "Waiting for uploads to complete..."
    $Jobs | Wait-Job -Timeout $TestConfig.TimeoutSeconds | Out-Null
    
    # Collect results
    $SuccessCount = 0
    $FailCount = 0
    $TotalDuration = 0
    
    foreach ($Job in $Jobs) {
        $JobResult = Receive-Job -Job $Job
        
        if ($JobResult.Success) {
            $SuccessCount++
            $TotalDuration += $JobResult.Duration
        }
        else {
            $FailCount++
        }
        
        Remove-Job -Job $Job -Force
    }
    
    $TotalTime = ((Get-Date) - $StartTime).TotalSeconds
    $AvgDuration = if ($SuccessCount -gt 0) { [math]::Round($TotalDuration / $SuccessCount, 2) } else { 0 }
    
    # Clean up test files
    foreach ($TestFile in $TestFiles) {
        if (Test-Path $TestFile) {
            Remove-Item $TestFile -Force
        }
    }
    
    $TestName = "Concurrent Upload - $UserCount users"
    
    if ($SuccessCount -eq $UserCount) {
        Write-TestSuccess "$TestName`: All uploads succeeded (Avg: $($AvgDuration)s, Total: $($TotalTime)s)"
        $Status = "PASS"
    }
    elseif ($SuccessCount -gt 0) {
        Write-TestWarning "$TestName`: Partial success ($SuccessCount/$UserCount succeeded)"
        $Status = "PARTIAL"
    }
    else {
        Write-TestFailure "$TestName`: All uploads failed"
        $Status = "FAIL"
    }
    
    $script:TestResults += [PSCustomObject]@{
        TestName = $TestName
        FileSize = "$([math]::Round($FileSize / 1MB, 2))MB"
        Status = $Status
        Duration = "$($TotalTime)s"
        Throughput = "$SuccessCount/$UserCount succeeded"
        FileId = $null
        Error = if ($FailCount -gt 0) { "$FailCount uploads failed" } else { $null }
    }
    
    $script:PerformanceMetrics += [PSCustomObject]@{
        TestName = $TestName
        FileSizeMB = [math]::Round($FileSize / 1MB, 2)
        DurationSeconds = $TotalTime
        ThroughputMBps = 0
        Success = ($Status -eq "PASS")
        ConcurrentUsers = $UserCount
        SuccessCount = $SuccessCount
        FailCount = $FailCount
        AvgDuration = $AvgDuration
    }
}

# Generate test report
function New-TestReport {
    Write-TestInfo "Generating test report..."
    
    $ReportFile = Join-Path $ReportDir "load_test_report_$Timestamp.md"
    $CsvFile = Join-Path $ReportDir "load_test_metrics_$Timestamp.csv"
    
    $Report = @"
# File Service Load Test Report

**Test Date:** $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
**Service URL:** $ServiceUrl
**App ID:** $AppId

## Test Summary

"@
    
    $TotalTests = $TestResults.Count
    $PassedTests = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
    $FailedTests = ($TestResults | Where-Object { $_.Status -in @("FAIL", "ERROR") }).Count
    $PartialTests = ($TestResults | Where-Object { $_.Status -eq "PARTIAL" }).Count
    
    $Report += @"
- **Total Tests:** $TotalTests
- **Passed:** $PassedTests
- **Failed:** $FailedTests
- **Partial:** $PartialTests
- **Success Rate:** $([math]::Round(($PassedTests / $TotalTests) * 100, 2))%

## Test Results

| Test Name | File Size | Status | Duration | Throughput | Error |
|-----------|-----------|--------|----------|------------|-------|

"@
    
    foreach ($Result in $TestResults) {
        $Error = if ($Result.Error) { $Result.Error } else { "-" }
        $Report += "| $($Result.TestName) | $($Result.FileSize) | $($Result.Status) | $($Result.Duration) | $($Result.Throughput) | $Error |`n"
    }
    
    $Report += @"

## Performance Metrics

### Single File Upload Performance

"@
    
    $SingleFileMetrics = $PerformanceMetrics | Where-Object { $_.TestName -notmatch "Concurrent" }
    
    if ($SingleFileMetrics) {
        $Report += "| File Size | Duration | Throughput |`n"
        $Report += "|-----------|----------|------------|`n"
        
        foreach ($Metric in $SingleFileMetrics) {
            $Report += "| $($Metric.FileSizeMB)MB | $($Metric.DurationSeconds)s | $($Metric.ThroughputMBps) MB/s |`n"
        }
    }
    
    $Report += @"

### Concurrent Upload Performance

"@
    
    $ConcurrentMetrics = $PerformanceMetrics | Where-Object { $_.TestName -match "Concurrent" }
    
    if ($ConcurrentMetrics) {
        $Report += "| Users | File Size | Total Duration | Success Rate | Avg Duration |`n"
        $Report += "|-------|-----------|----------------|--------------|--------------|`n"
        
        foreach ($Metric in $ConcurrentMetrics) {
            $SuccessRate = [math]::Round(($Metric.SuccessCount / $Metric.ConcurrentUsers) * 100, 2)
            $Report += "| $($Metric.ConcurrentUsers) | $($Metric.FileSizeMB)MB | $($Metric.DurationSeconds)s | $SuccessRate% | $($Metric.AvgDuration)s |`n"
        }
    }
    
    $Report += @"

## Recommendations

"@
    
    # Add recommendations based on results
    if ($FailedTests -gt 0) {
        $Report += "- ⚠️ Some tests failed. Review error messages and check service logs.`n"
    }
    
    $LargeFileMetrics = $SingleFileMetrics | Where-Object { $_.FileSizeMB -ge 50 }
    if ($LargeFileMetrics) {
        $AvgThroughput = ($LargeFileMetrics | Measure-Object -Property ThroughputMBps -Average).Average
        if ($AvgThroughput -lt 5) {
            $Report += "- ⚠️ Large file upload throughput is low ($([math]::Round($AvgThroughput, 2)) MB/s). Consider optimizing network or storage.`n"
        }
        else {
            $Report += "- ✅ Large file upload performance is acceptable ($([math]::Round($AvgThroughput, 2)) MB/s average).`n"
        }
    }
    
    if ($ConcurrentMetrics) {
        $HighLoadTest = $ConcurrentMetrics | Sort-Object -Property ConcurrentUsers -Descending | Select-Object -First 1
        if ($HighLoadTest.SuccessCount -eq $HighLoadTest.ConcurrentUsers) {
            $Report += "- ✅ System handled $($HighLoadTest.ConcurrentUsers) concurrent users successfully.`n"
        }
        else {
            $Report += "- ⚠️ System struggled with $($HighLoadTest.ConcurrentUsers) concurrent users ($($HighLoadTest.SuccessCount)/$($HighLoadTest.ConcurrentUsers) succeeded).`n"
        }
    }
    
    $Report += "`n---`n`n*Report generated by file-service load testing script*`n"
    
    # Save report
    [System.IO.File]::WriteAllText($ReportFile, $Report)
    Write-TestSuccess "Report saved to: $ReportFile"
    
    # Save CSV metrics
    $PerformanceMetrics | Export-Csv -Path $CsvFile -NoTypeInformation
    Write-TestSuccess "Metrics saved to: $CsvFile"
}

# Main test execution
function Start-LoadTests {
    Write-TestHeader "File Service Load Testing"
    
    Write-Host "Configuration:" -ForegroundColor Cyan
    Write-Host "  Service URL: $ServiceUrl" -ForegroundColor Gray
    Write-Host "  App ID: $AppId" -ForegroundColor Gray
    Write-Host "  Token: $(if ($TestToken) { 'Provided' } else { 'None (testing without auth)' })" -ForegroundColor Gray
    Write-Host "  Concurrent Users: $ConcurrentUsers" -ForegroundColor Gray
    Write-Host ""
    
    Initialize-TestEnvironment
    
    # Section 1: Large File Upload Tests
    Write-TestHeader "Section 1: Large File Upload Tests"
    
    Test-SingleFileUpload -TestName "Upload 1MB File" -FileSize $TestConfig.SmallFileSize
    Test-SingleFileUpload -TestName "Upload 10MB File" -FileSize $TestConfig.MediumFileSize
    Test-SingleFileUpload -TestName "Upload 50MB File" -FileSize $TestConfig.LargeFileSize
    Test-SingleFileUpload -TestName "Upload 100MB File" -FileSize $TestConfig.XLargeFileSize
    
    # Section 2: Concurrent Upload Tests
    Write-TestHeader "Section 2: Concurrent Upload Tests"
    
    foreach ($UserCount in $TestConfig.ConcurrentTests) {
        if ($UserCount -le $ConcurrentUsers) {
            Test-ConcurrentUploads -UserCount $UserCount -FileSize $TestConfig.SmallFileSize
        }
    }
    
    # Generate report
    if ($GenerateReport) {
        Write-TestHeader "Generating Report"
        New-TestReport
    }
    
    # Display summary
    Write-TestHeader "Test Summary"
    
    $TotalTests = $TestResults.Count
    $PassedTests = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
    $FailedTests = ($TestResults | Where-Object { $_.Status -in @("FAIL", "ERROR") }).Count
    
    Write-Host "Total Tests: $TotalTests" -ForegroundColor Cyan
    Write-Host "Passed: $PassedTests" -ForegroundColor Green
    Write-Host "Failed: $FailedTests" -ForegroundColor $(if ($FailedTests -gt 0) { "Red" } else { "Green" })
    Write-Host "Success Rate: $([math]::Round(($PassedTests / $TotalTests) * 100, 2))%" -ForegroundColor Cyan
    
    # Cleanup
    if (-not $SkipCleanup) {
        Write-TestInfo "Cleaning up temporary files..."
        if (Test-Path $TempDir) {
            Remove-Item $TempDir -Recurse -Force
        }
    }
    
    Write-Host "`nTest execution completed!" -ForegroundColor Cyan
}

# Run tests
Start-LoadTests
