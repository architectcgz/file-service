# File Service Client

Java 客户端库，用于与 File Service 平台集成，提供文件上传、下载和管理功能。

## 特性

- ✅ 多种上传方式：直接上传、分片上传、预签名 URL 上传
- ✅ 秒传支持：基于文件哈希的去重上传
- ✅ 访问级别控制：支持公共和私有文件
- ✅ 自动租户隔离：通过 X-App-Id 头自动隔离租户数据
- ✅ 灵活的认证：支持静态令牌和动态令牌提供者
- ✅ 连接池管理：高效的 HTTP 连接复用
- ✅ 自定义域名：支持 CDN 和自定义域名配置
- ✅ 完善的错误处理：类型化异常，清晰的错误信息
- ✅ 日志支持：使用 SLF4J 进行日志记录

## 安装

### Maven

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>file-service-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.platform:file-service-client:1.0.0-SNAPSHOT'
```

## 快速开始

### 1. 创建客户端

```java
import com.platform.fileservice.client.*;
import com.platform.fileservice.client.config.*;

// 创建配置
FileServiceClientConfig config = FileServiceClientConfig.builder()
        .serverUrl("http://localhost:8089")
        .tenantId("your-app-id")
        .tokenProvider(TokenProvider.fixed("your-jwt-token"))
        .build();

// 创建客户端
FileServiceClient client = new FileServiceClientImpl(config);
```

### 2. 上传文件

```java
// 上传图片
File imageFile = new File("/path/to/image.jpg");
FileUploadResponse response = client.uploadImage(imageFile);

System.out.println("文件ID: " + response.getFileId());
System.out.println("访问URL: " + response.getUrl());
```

### 3. 获取文件信息

```java
// 获取文件 URL
String fileUrl = client.getFileUrl(fileId);

// 获取文件详情
FileDetailResponse detail = client.getFileDetail(fileId);
System.out.println("文件名: " + detail.getOriginalName());
System.out.println("文件大小: " + detail.getFileSize());
```

### 4. 删除文件

```java
client.deleteFile(fileId);
```

### 5. 关闭客户端

```java
client.close();
```

## 配置选项

### 必需配置

| 参数 | 类型 | 说明 |
|------|------|------|
| `serverUrl` | String | File Service 服务器地址 |
| `tenantId` | String | 租户 ID（应用标识） |
| `tokenProvider` | TokenProvider | 认证令牌提供者 |

### 可选配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `connectTimeout` | int | 10000 | 连接超时（毫秒） |
| `readTimeout` | int | 30000 | 读取超时（毫秒） |
| `maxConnections` | int | 50 | 最大连接数 |
| `customDomain` | String | null | 自定义域名 |
| `cdnDomain` | String | null | CDN 域名（用于公共文件） |
| `maxRetries` | int | 3 | 最大重试次数 |
| `retryDelayMs` | long | 1000 | 重试延迟（毫秒） |

### 配置示例

```java
FileServiceClientConfig config = FileServiceClientConfig.builder()
        .serverUrl("http://localhost:8089")
        .tenantId("blog")
        .tokenProvider(TokenProvider.fixed("eyJhbGciOiJIUzI1NiIs..."))
        .connectTimeout(15000)
        .readTimeout(60000)
        .maxConnections(100)
        .customDomain("https://files.example.com")
        .cdnDomain("https://cdn.example.com")
        .maxRetries(5)
        .retryDelayMs(2000)
        .build();
```

## 使用指南

### 认证令牌提供者

#### 静态令牌

```java
TokenProvider tokenProvider = TokenProvider.fixed("your-jwt-token");
```

#### 动态令牌

```java
TokenProvider tokenProvider = TokenProvider.fromSupplier(() -> {
    // 从某处获取当前有效的令牌
    return getCurrentToken();
});
```

#### 自定义实现

```java
TokenProvider tokenProvider = new TokenProvider() {
    @Override
    public String getToken() {
        // 自定义令牌获取逻辑
        return fetchTokenFromAuthService();
    }
};
```

### 文件上传

#### 上传图片

```java
// 从文件上传
File imageFile = new File("/path/to/image.jpg");
FileUploadResponse response = client.uploadImage(imageFile);

// 从 InputStream 上传
try (InputStream is = new FileInputStream(imageFile)) {
    FileUploadResponse response = client.uploadImage(
            is, 
            "image.jpg", 
            imageFile.length());
}
```

#### 上传普通文件

```java
// 默认为公共文件
File file = new File("/path/to/document.pdf");
FileUploadResponse response = client.uploadFile(file);

// 指定访问级别
FileUploadResponse response = client.uploadFile(file, AccessLevel.PRIVATE);

// 从 InputStream 上传
try (InputStream is = new FileInputStream(file)) {
    FileUploadResponse response = client.uploadFile(
            is, 
            "document.pdf", 
            file.length(), 
            "application/pdf");
}
```

### 秒传（即时上传）

秒传功能通过文件哈希检查文件是否已存在，如果存在则直接返回文件信息，无需重复上传。

```java
// 计算文件哈希
String fileHash = calculateMD5(file);

// 检查文件是否已存在
InstantUploadCheckRequest request = InstantUploadCheckRequest.builder()
        .fileHash(fileHash)
        .fileName(file.getName())
        .fileSize(file.length())
        .contentType("image/jpeg")
        .accessLevel(AccessLevel.PUBLIC)
        .build();

InstantUploadCheckResponse response = client.checkInstantUpload(request);

if (response.isExists()) {
    // 文件已存在，秒传成功
    System.out.println("秒传成功! 文件ID: " + response.getFileId());
    System.out.println("文件URL: " + response.getUrl());
} else {
    // 文件不存在，需要正常上传
    FileUploadResponse uploadResponse = client.uploadFile(file);
}
```

### 大文件分片上传

对于大文件（>10MB），建议使用分片上传以提高可靠性和性能。

```java
File largeFile = new File("/path/to/large-file.zip");
long chunkSize = 5 * 1024 * 1024; // 5MB per chunk

// 1. 初始化分片上传
MultipartInitRequest initRequest = MultipartInitRequest.builder()
        .fileName(largeFile.getName())
        .fileSize(largeFile.length())
        .contentType("application/zip")
        .chunkSize(chunkSize)
        .accessLevel(AccessLevel.PUBLIC)
        .build();

MultipartInitResponse initResponse = client.initMultipartUpload(initRequest);
String taskId = initResponse.getTaskId();

try {
    // 2. 上传各个分片
    List<MultipartUploadPart> parts = new ArrayList<>();
    
    try (FileInputStream fis = new FileInputStream(largeFile)) {
        byte[] buffer = new byte[(int) chunkSize];
        int partNumber = 1;
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) > 0) {
            ByteArrayInputStream partStream = new ByteArrayInputStream(buffer, 0, bytesRead);
            
            MultipartUploadPart part = client.uploadPart(
                    taskId, 
                    partNumber, 
                    partStream, 
                    bytesRead);
            
            parts.add(part);
            partNumber++;
        }
    }
    
    // 3. 完成上传
    String fileHash = calculateMD5(largeFile);
    FileUploadResponse response = client.completeMultipartUpload(taskId, fileHash);
    
    System.out.println("分片上传完成! 文件ID: " + response.getFileId());
    
} catch (Exception e) {
    // 上传失败，取消任务
    client.cancelMultipartUpload(taskId);
    throw e;
}
```

### 预签名 URL 上传

预签名 URL 允许客户端直接上传到 S3，无需通过服务器中转。

```java
// 1. 获取预签名上传 URL
PresignedUploadRequest request = PresignedUploadRequest.builder()
        .fileName("document.pdf")
        .fileSize(1024000)
        .contentType("application/pdf")
        .accessLevel(AccessLevel.PUBLIC)
        .build();

PresignedUploadResponse response = client.getPresignedUploadUrl(request);

// 2. 使用预签名 URL 直接上传到 S3
// (这部分需要使用 HTTP 客户端直接上传)
uploadToS3(response.getUploadUrl(), file);

// 3. 确认上传完成
String fileHash = calculateMD5(file);
FileUploadResponse finalResponse = client.confirmPresignedUpload(
        response.getFileId(), 
        fileHash);
```

### 文件访问

```java
// 获取文件访问 URL
String fileUrl = client.getFileUrl(fileId);

// 获取文件详细信息
FileDetailResponse detail = client.getFileDetail(fileId);

System.out.println("文件ID: " + detail.getFileId());
System.out.println("文件名: " + detail.getOriginalName());
System.out.println("文件大小: " + detail.getFileSize() + " bytes");
System.out.println("内容类型: " + detail.getContentType());
System.out.println("访问URL: " + detail.getUrl());
System.out.println("创建时间: " + detail.getCreatedAt());
System.out.println("访问级别: " + detail.getAccessLevel());
```

### 文件删除

```java
try {
    client.deleteFile(fileId);
    System.out.println("文件删除成功");
} catch (FileNotFoundException e) {
    System.err.println("文件不存在");
} catch (AccessDeniedException e) {
    System.err.println("无权删除该文件");
}
```

### 自定义域名和 CDN

配置自定义域名后，客户端会自动替换返回的文件 URL 中的域名部分。

```java
FileServiceClientConfig config = FileServiceClientConfig.builder()
        .serverUrl("http://localhost:8089")
        .tenantId("blog")
        .tokenProvider(tokenProvider)
        .customDomain("https://files.example.com")  // 自定义域名
        .cdnDomain("https://cdn.example.com")       // CDN 域名（用于公共文件）
        .build();

FileServiceClient client = new FileServiceClientImpl(config);

// 上传公共文件
FileUploadResponse response = client.uploadFile(file, AccessLevel.PUBLIC);
// response.getUrl() 将使用 CDN 域名

// 上传私有文件
FileUploadResponse response2 = client.uploadFile(file, AccessLevel.PRIVATE);
// response2.getUrl() 将使用自定义域名
```

## 异常处理

客户端提供了类型化的异常，便于精确处理不同的错误情况。

### 异常层次结构

```
FileServiceException (RuntimeException)
├── InvalidRequestException (400)
├── AuthenticationException (401)
├── AccessDeniedException (403)
├── FileNotFoundException (404)
├── QuotaExceededException (413)
├── NetworkException (网络错误)
└── ParseException (JSON 解析错误)
```

### 异常处理示例

```java
try {
    FileUploadResponse response = client.uploadImage(file);
    System.out.println("上传成功: " + response.getFileId());
    
} catch (InvalidRequestException e) {
    // 400 - 请求参数无效
    System.err.println("无效的请求参数: " + e.getMessage());
    
} catch (AuthenticationException e) {
    // 401 - 认证失败
    System.err.println("认证失败，请检查令牌: " + e.getMessage());
    
} catch (AccessDeniedException e) {
    // 403 - 无权访问
    System.err.println("无权访问该资源: " + e.getMessage());
    
} catch (FileNotFoundException e) {
    // 404 - 文件不存在
    System.err.println("文件不存在: " + e.getMessage());
    
} catch (QuotaExceededException e) {
    // 413 - 文件大小超过限制
    System.err.println("文件大小超过限制: " + e.getMessage());
    
} catch (NetworkException e) {
    // 网络错误
    System.err.println("网络错误: " + e.getMessage());
    
} catch (ParseException e) {
    // JSON 解析错误
    System.err.println("响应解析失败: " + e.getMessage());
    
} catch (FileServiceException e) {
    // 其他错误
    System.err.println("文件服务错误: " + e.getMessage());
}
```

## 日志配置

客户端使用 SLF4J 进行日志记录。你可以通过配置日志级别来控制日志输出。

### Logback 配置示例

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File Service Client 日志 -->
    <logger name="com.platform.fileservice.client" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### 日志级别说明

- **DEBUG**: 记录请求 URL、方法、响应状态码
- **ERROR**: 记录错误详情

**注意**: 客户端不会记录敏感信息（如认证令牌、文件内容）。

## 最佳实践

### 1. 使用 try-with-resources

```java
try (FileServiceClient client = new FileServiceClientImpl(config)) {
    FileUploadResponse response = client.uploadImage(file);
    // 使用 response
} // 自动关闭客户端
```

### 2. 复用客户端实例

客户端实例是线程安全的，应该复用而不是每次请求都创建新实例。

```java
// ✅ 好的做法：复用客户端
@Service
public class FileService {
    private final FileServiceClient client;
    
    public FileService(FileServiceClientConfig config) {
        this.client = new FileServiceClientImpl(config);
    }
    
    public FileUploadResponse upload(File file) {
        return client.uploadImage(file);
    }
}

// ❌ 不好的做法：每次创建新客户端
public FileUploadResponse upload(File file) {
    FileServiceClient client = new FileServiceClientImpl(config);
    return client.uploadImage(file);
}
```

### 3. 大文件使用分片上传

对于大于 10MB 的文件，建议使用分片上传以提高可靠性。

```java
if (file.length() > 10 * 1024 * 1024) {
    // 使用分片上传
    return multipartUpload(file);
} else {
    // 使用直接上传
    return client.uploadFile(file);
}
```

### 4. 使用秒传优化性能

对于可能重复上传的文件，先检查是否已存在。

```java
// 先检查秒传
InstantUploadCheckResponse checkResponse = client.checkInstantUpload(request);
if (checkResponse.isExists()) {
    return buildResponse(checkResponse);
}

// 文件不存在，正常上传
return client.uploadFile(file);
```

### 5. 正确处理异常

根据不同的异常类型采取不同的处理策略。

```java
try {
    return client.uploadFile(file);
} catch (QuotaExceededException e) {
    // 文件太大，提示用户
    throw new BusinessException("文件大小超过限制");
} catch (AuthenticationException e) {
    // 认证失败，刷新令牌
    refreshToken();
    return client.uploadFile(file);
} catch (NetworkException e) {
    // 网络错误，重试
    return retryUpload(file);
}
```

## 线程安全

`FileServiceClient` 实例是线程安全的，可以在多线程环境中安全使用。

```java
// 单例客户端
private static final FileServiceClient client = new FileServiceClientImpl(config);

// 多线程并发上传
ExecutorService executor = Executors.newFixedThreadPool(10);
List<Future<FileUploadResponse>> futures = new ArrayList<>();

for (File file : files) {
    Future<FileUploadResponse> future = executor.submit(() -> 
            client.uploadFile(file));
    futures.add(future);
}

// 等待所有上传完成
for (Future<FileUploadResponse> future : futures) {
    FileUploadResponse response = future.get();
    System.out.println("上传完成: " + response.getFileId());
}
```

## 示例项目

查看完整的示例项目：[file-service-example](../examples/file-service-example)

## Spring Boot 集成

如果你使用 Spring Boot，推荐使用 [file-service-spring-boot-starter](../file-service-spring-boot-starter) 以获得自动配置支持。

## API 参考

详细的 API 文档请参考 JavaDoc。

## 许可证

本项目采用 MIT 许可证。
