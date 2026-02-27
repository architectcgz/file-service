# 简单的文件上传测试脚本

$url = "http://localhost:8090/api/examples/upload-image"
$filePath = "test-upload.txt"

Write-Host "Testing file upload to: $url"
Write-Host "File: $filePath"

try {
    # 使用 Invoke-WebRequest 上传文件
    $response = Invoke-WebRequest -Uri $url -Method Post -InFile $filePath -ContentType "multipart/form-data" -TimeoutSec 30
    
    Write-Host "Success! Status Code: $($response.StatusCode)"
    Write-Host "Response:"
    $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
    
} catch {
    Write-Host "Error: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body: $responseBody"
    }
}
