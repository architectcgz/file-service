# File Service Example Application

这是一个演示如何使用 File Service Client SDK 的 Spring Boot 示例应用程序。

## 功能演示

本示例应用演示了以下功能：

1. **基本图片上传** - 直接上传图片文件
2. **私有文件上传** - 上传私有访问级别的文件
3. **秒传（即时上传）** - 使用文件哈希检查文件是否已存在
4. **大文件分片上传** - 使用分片上传处理大文件
5. **获取文件信息** - 获取文件 URL 和详细元数据
6. **删除文件** - 删除已上传的文件

## 快速开始

### 1. 配置

编辑 `src/main/resources/application.yml` 文件，配置 File Service 连接信息：

```yaml
file-service:
  client:
    server-url: http://localhost:8089
    tenant-id: your-app-id
    token: your-jwt-token
```

或者通过环境变量设置：

```bash
export FILE_SERVICE_TOKEN=your-jwt-token
```

### 2. 运行应用

```bash
mvn spring-boot:run
```

应用将在 `http://localhost:8090` 启动。

### 3. 测试 API

#### 上传图片

```bash
curl -X POST http://localhost:8090/api/examples/upload-image \
  -F "file=@/path/to/image.jpg"
```

#### 上传私有文件

```bash
curl -X POST http://localhost:8090/api/examples/upload-private \
  -F "file=@/path/to/document.pdf"
```

#### 秒传

```bash
curl -X POST http://localhost:8090/api/examples/instant-upload \
  -F "file=@/path/to/file.txt"
```

#### 大文件分片上传

```bash
curl -X POST http://localhost:8090/api/examples/multipart-upload \
  -F "file=@/path/to/large-file.zip"
```

#### 获取文件 URL

```bash
curl http://localhost:8090/api/examples/file-url/{fileId}
```

#### 获取文件详情

```bash
curl http://localhost:8090/api/examples/file-detail/{fileId}
```

#### 删除文件

```bash
curl -X DELETE http://localhost:8090/api/examples/file/{fileId}
```

## 代码示例

### 基本上传

```java
@Service
@RequiredArgsConstructor
public class FileUploadService {
    
    private final FileServiceClient fileServiceClient;
    
    public FileUploadResponse uploadImage(File imageFile) {
        return fileServiceClient.uploadImage(imageFile);
    }
}
```

### 秒传

```java
public FileUploadResponse instantUpload(File file) {
    // 计算文件哈希
    String fileHash = calculateMD5(file);
    
    // 检查文件是否已存在
    InstantUploadCheckRequest request = InstantUploadCheckRequest.builder()
            .fileHash(fileHash)
            .fileName(file.getName())
            .fileSize(file.length())
            .contentType("image/jpeg")
            .build();
    
    InstantUploadCheckResponse response = 
            fileServiceClient.checkInstantUpload(request);
    
    if (response.isExists()) {
        // 文件已存在，秒传成功
        return buildResponse(response);
    } else {
        // 文件不存在，正常上传
        return fileServiceClient.uploadFile(file);
    }
}
```

### 分片上传

```java
public FileUploadResponse multipartUpload(File file) {
    // 1. 初始化分片上传
    MultipartInitRequest initRequest = MultipartInitRequest.builder()
            .fileName(file.getName())
            .fileSize(file.length())
            .contentType("application/octet-stream")
            .chunkSize(5 * 1024 * 1024) // 5MB
            .build();
    
    MultipartInitResponse initResponse = 
            fileServiceClient.initMultipartUpload(initRequest);
    
    // 2. 上传各个分片
    try (FileInputStream fis = new FileInputStream(file)) {
        byte[] buffer = new byte[5 * 1024 * 1024];
        int partNumber = 1;
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) > 0) {
            ByteArrayInputStream partStream = 
                    new ByteArrayInputStream(buffer, 0, bytesRead);
            
            fileServiceClient.uploadPart(
                    initResponse.getTaskId(), 
                    partNumber++, 
                    partStream, 
                    bytesRead);
        }
    }
    
    // 3. 完成上传
    String fileHash = calculateMD5(file);
    return fileServiceClient.completeMultipartUpload(
            initResponse.getTaskId(), fileHash);
}
```

### 错误处理

```java
try {
    FileUploadResponse response = fileServiceClient.uploadImage(file);
    log.info("上传成功: {}", response.getFileId());
    
} catch (InvalidRequestException e) {
    log.error("无效的请求参数: {}", e.getMessage());
} catch (AuthenticationException e) {
    log.error("认证失败: {}", e.getMessage());
} catch (QuotaExceededException e) {
    log.error("文件大小超过限制: {}", e.getMessage());
} catch (FileServiceException e) {
    log.error("文件上传失败: {}", e.getMessage());
}
```

## 项目结构

```
src/main/java/com/platform/example/
├── FileServiceExampleApplication.java    # 主应用类
├── controller/
│   └── FileExampleController.java        # REST API 控制器
└── service/
    ├── FileUploadExampleService.java     # 文件上传示例
    └── FileAccessExampleService.java     # 文件访问示例
```

## 更多信息

- [File Service Client 文档](../../file-service-client/README.md)
- [Spring Boot Starter 文档](../../file-service-spring-boot-starter/README.md)
