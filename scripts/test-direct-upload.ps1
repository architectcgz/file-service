# S3 直传上传测试脚本
# 用于快速测试直传上传功能

param(
    [string]$ApiUrl = "http://localhost:8089",
    [string]$AppId = "test-app",
    [string]$UserId = "test-user",
    [string]$FilePath = "",
    [int]$ChunkSize = 5242880  # 5MB
)

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

# 检查文件是否存在
if (-not $FilePath) {
    Write-ColorOutput "错误: 请指定要上传的文件路径" "Red"
    Write-ColorOutput "用法: .\test-direct-upload.ps1 -FilePath 'C:\path\to\file.txt'" "Yellow"
    exit 1
}

if (-not (Test-Path $FilePath)) {
    Write-ColorOutput "错误: 文件不存在: $FilePath" "Red"
    exit 1
}

$file = Get-Item $FilePath
$fileName = $file.Name
$fileSize = $file.Length
$contentType = "application/octet-stream"

Write-ColorOutput "`n========== S3 直传上传测试 ==========" "Cyan"
Write-ColorOutput "API 地址: $ApiUrl" "Gray"
Write-ColorOutput "App ID: $AppId" "Gray"
Write-ColorOutput "User ID: $UserId" "Gray"
Write-ColorOutput "文件: $fileName" "Gray"
Write-ColorOutput "大小: $([math]::Round($fileSize / 1MB, 2)) MB" "Gray"
Write-ColorOutput "======================================`n" "Cyan"

# 步骤 1: 初始化上传
Write-ColorOutput "[1/4] 初始化上传..." "Yellow"

$initRequest = @{
    fileName = $fileName
    fileSize = $fileSize
    contentType = $contentType
    fileHash = "$fileName-$fileSize-$($file.LastWriteTime.Ticks)"
} | ConvertTo-Json

try {
    $initResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/direct-upload/init" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/json"
            "X-App-Id" = $AppId
            "X-User-Id" = $UserId
        } `
        -Body $initRequest

    if ($initResponse.code -ne 200) {
        Write-ColorOutput "初始化失败: $($initResponse.message)" "Red"
        exit 1
    }

    $taskId = $initResponse.data.taskId
    $uploadId = $initResponse.data.uploadId
    $chunkSize = $initResponse.data.chunkSize
    $totalParts = $initResponse.data.totalParts
    $completedParts = $initResponse.data.completedParts

    Write-ColorOutput "✓ 初始化成功" "Green"
    Write-ColorOutput "  Task ID: $taskId" "Gray"
    Write-ColorOutput "  Upload ID: $uploadId" "Gray"
    Write-ColorOutput "  分片大小: $([math]::Round($chunkSize / 1MB, 2)) MB" "Gray"
    Write-ColorOutput "  总分片数: $totalParts" "Gray"
    
    if ($completedParts.Count -gt 0) {
        Write-ColorOutput "  已完成分片: $($completedParts.Count) (断点续传)" "Cyan"
    }
} catch {
    Write-ColorOutput "初始化失败: $_" "Red"
    exit 1
}

# 步骤 2: 上传所有分片
Write-ColorOutput "`n[2/4] 上传分片..." "Yellow"

$fileStream = [System.IO.File]::OpenRead($FilePath)
$partInfos = @()
$uploadedBytes = 0

try {
    for ($partNumber = 1; $partNumber -le $totalParts; $partNumber++) {
        # 跳过已完成的分片
        if ($completedParts -contains $partNumber) {
            Write-ColorOutput "  跳过分片 $partNumber/$totalParts (已完成)" "Gray"
            $uploadedBytes += $chunkSize
            continue
        }

        # 获取上传 URL
        $urlRequest = @{
            taskId = $taskId
            partNumbers = @($partNumber)
        } | ConvertTo-Json

        $urlResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/direct-upload/part-urls" `
            -Method Post `
            -Headers @{
                "Content-Type" = "application/json"
                "X-User-Id" = $UserId
            } `
            -Body $urlRequest

        if ($urlResponse.code -ne 200) {
            Write-ColorOutput "  获取上传URL失败: $($urlResponse.message)" "Red"
            throw "获取上传URL失败"
        }

        $uploadUrl = $urlResponse.data.partUrls[0].uploadUrl

        # 读取分片数据
        $buffer = New-Object byte[] $chunkSize
        $bytesRead = $fileStream.Read($buffer, 0, $chunkSize)
        
        if ($bytesRead -lt $chunkSize) {
            $buffer = $buffer[0..($bytesRead - 1)]
        }

        # 上传分片
        $uploadResponse = Invoke-WebRequest -Uri $uploadUrl `
            -Method Put `
            -Body $buffer `
            -Headers @{
                "Content-Type" = "application/octet-stream"
            }

        if ($uploadResponse.StatusCode -ne 200) {
            Write-ColorOutput "  上传分片 $partNumber 失败" "Red"
            throw "上传分片失败"
        }

        # 获取 ETag
        $etag = $uploadResponse.Headers["ETag"] -replace '"', ''
        
        $partInfos += @{
            partNumber = $partNumber
            etag = $etag
        }

        $uploadedBytes += $bytesRead
        $percentage = [math]::Round(($uploadedBytes / $fileSize) * 100, 1)
        
        Write-ColorOutput "  ✓ 分片 $partNumber/$totalParts ($percentage%) - ETag: $etag" "Green"
    }
} finally {
    $fileStream.Close()
}

Write-ColorOutput "✓ 所有分片上传完成" "Green"
Write-ColorOutput "  共收集 $($partInfos.Count) 个分片信息" "Gray"

# 步骤 3: 完成上传
Write-ColorOutput "`n[3/4] 完成上传..." "Yellow"

$completeRequest = @{
    taskId = $taskId
    contentType = $contentType
    parts = $partInfos
} | ConvertTo-Json -Depth 10

Write-ColorOutput "  发送请求: $completeRequest" "Gray"

try {
    $completeResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/direct-upload/complete" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/json"
            "X-App-Id" = $AppId
            "X-User-Id" = $UserId
        } `
        -Body $completeRequest

    if ($completeResponse.code -ne 200) {
        Write-ColorOutput "完成上传失败: $($completeResponse.message)" "Red"
        exit 1
    }

    $fileId = $completeResponse.data
    Write-ColorOutput "✓ 上传完成" "Green"
    Write-ColorOutput "  文件ID: $fileId" "Gray"
} catch {
    Write-ColorOutput "完成上传失败: $_" "Red"
    exit 1
}

# 步骤 4: 验证文件
Write-ColorOutput "`n[4/4] 验证文件..." "Yellow"

try {
    $fileResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/files/$fileId" `
        -Method Get `
        -Headers @{
            "X-App-Id" = $AppId
            "X-User-Id" = $UserId
        }

    if ($fileResponse.code -ne 200) {
        Write-ColorOutput "验证失败: $($fileResponse.message)" "Red"
        exit 1
    }

    Write-ColorOutput "✓ 文件验证成功" "Green"
    Write-ColorOutput "  文件名: $($fileResponse.data.originalFilename)" "Gray"
    Write-ColorOutput "  大小: $([math]::Round($fileResponse.data.fileSize / 1MB, 2)) MB" "Gray"
    Write-ColorOutput "  状态: $($fileResponse.data.status)" "Gray"
} catch {
    Write-ColorOutput "验证失败: $_" "Yellow"
}

Write-ColorOutput "`n========== 测试完成 ==========" "Cyan"
Write-ColorOutput "✓ 文件上传成功!" "Green"
Write-ColorOutput "文件ID: $fileId" "White"
Write-ColorOutput "==============================`n" "Cyan"
