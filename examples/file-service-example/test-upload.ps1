# 测试文件上传脚本

$uri = "http://localhost:8090/api/examples/upload-image"
$filePath = "test-upload.txt"

# 使用curl.exe (真正的curl，不是PowerShell别名)
curl.exe -X POST -F "file=@$filePath" $uri
