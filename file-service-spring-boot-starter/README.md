# File Service Spring Boot Starter

Spring Boot 自动配置模块，用于快速集成 File Service Client SDK。

## 特性

- ✅ 自动配置：零代码配置，开箱即用
- ✅ 属性绑定：通过 `application.yml` 配置所有选项
- ✅ Spring Security 集成：自动从 Security Context 获取令牌
- ✅ 依赖注入：自动注册 `FileServiceClient` Bean
- ✅ 条件装配：支持自定义 Bean 覆盖

## 安装

### Maven

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>file-service-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.platform:file-service-spring-boot-starter:1.0.0-SNAPSHOT'
```

## 快速开始

### 1. 添加依赖

在 `pom.xml` 中添加 starter 依赖（如上所示）。

### 2. 配置属性

在 `application.yml` 中配置 File Service 连接信息：

```yaml
file-service:
  client:
    server-url: http://localhost:8089
    tenant-id: your-app-id
    token: your-jwt-token
```

### 3. 注入使用

在你的服务中注入 `FileServiceClient` 并使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final FileServiceClient fileServiceClient;
    
    public FileUploadResponse uploadFile(File file) {
        return fileServiceClient.uploadImage(file);
    }
}
```

就这么简单！无需任何额外配置代码。

## 配置属性

### 完整配置示例

```yaml
file-service:
  client:
    # 必需配置
    server-url: http://localhost:8089      # File Service 服务器地址
    tenant-id: blog                        # 租户 ID（应用标识）
    token: ${JWT_TOKEN}                    # 认证令牌（可选，如果使用 Spring Security）
    
    # 可选配置 - 连接设置
    connect-timeout: 10000                 # 连接超时（毫秒），默认 10000
    read-timeout: 30000                    # 读取超时（毫秒），默认 30000
    max-connections: 50                    # 最大连接数，默认 50
    
    # 可选配置 - 域名设置
    custom-domain: https://files.example.com   # 自定义域名
    cdn-domain: https://cdn.example.com        # CDN 域名（用于公共文件）
    
    # 可选配置 - 重试设置
    max-retries: 3                         # 最大重试次数，默认 3
    retry-delay-ms: 1000                   # 重试延迟（毫秒），默认 1000
```

### 配置属性说明

#### 必需属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `file-service.client.server-url` | String | File Service 服务器地址 |
| `file-service.client.tenant-id` | String | 租户 ID（应用标识） |

#### 可选属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `file-service.client.token` | String | null | 静态认证令牌（如果使用 Spring Security 则可选） |
| `file-service.client.connect-timeout` | int | 10000 | 连接超时（毫秒） |
| `file-service.client.read-timeout` | int | 30000 | 读取超时（毫秒） |
| `file-service.client.max-connections` | int | 50 | 最大连接数 |
| `file-service.client.custom-domain` | String | null | 自定义域名 |
| `file-service.client.cdn-domain` | String | null | CDN 域名（用于公共文件） |
| `file-service.client.max-retries` | int | 3 | 最大重试次数 |
| `file-service.client.retry-delay-ms` | long | 1000 | 重试延迟（毫秒） |

### 使用环境变量

推荐使用环境变量来配置敏感信息：

```yaml
file-service:
  client:
    server-url: ${FILE_SERVICE_URL:http://localhost:8089}
    tenant-id: ${FILE_SERVICE_TENANT_ID:blog}
    token: ${FILE_SERVICE_TOKEN}
```

然后在运行时设置环境变量：

```bash
export FILE_SERVICE_URL=https://file-service.example.com
export FILE_SERVICE_TENANT_ID=blog
export FILE_SERVICE_TOKEN=eyJhbGciOiJIUzI1NiIs...
```

## Spring Security 集成

如果你的应用使用了 Spring Security，starter 会自动从 Security Context 中获取认证令牌。

### 自动令牌提取

当配置中没有提供静态 `token` 时，starter 会尝试从以下来源获取令牌：

1. Spring Security Context 中的 JWT 令牌
2. OAuth2 Authentication 中的 Access Token

```yaml
file-service:
  client:
    server-url: http://localhost:8089
    tenant-id: blog
    # 不需要配置 token，会自动从 Security Context 获取
```

### 自定义 TokenProvider

如果需要自定义令牌获取逻辑，可以提供自己的 `TokenProvider` Bean：

```java
@Configuration
public class FileServiceConfig {
    
    @Bean
    public TokenProvider fileServiceTokenProvider() {
        return () -> {
            // 自定义令牌获取逻辑
            return getTokenFromCustomSource();
        };
    }
}
```

## 自定义配置

### 覆盖默认 Bean

如果需要完全自定义客户端配置，可以提供自己的 `FileServiceClient` Bean：

```java
@Configuration
public class FileServiceConfig {
    
    @Bean
    public FileServiceClient fileServiceClient() {
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl("http://localhost:8089")
                .tenantId("blog")
                .tokenProvider(TokenProvider.fixed("custom-token"))
                .connectTimeout(20000)
                .readTimeout(60000)
                .build();
        
        return new FileServiceClientImpl(config);
    }
}
```

### 自定义 TokenProvider

```java
@Configuration
public class FileServiceConfig {
    
    @Bean
    public TokenProvider fileServiceTokenProvider(AuthService authService) {
        return () -> authService.getCurrentToken();
    }
}
```

## 使用示例

### 基本文件上传

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileServiceClient fileServiceClient;
    
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file) throws IOException {
        
        // 转换为临时文件
        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        file.transferTo(tempFile);
        
        // 上传文件
        FileUploadResponse response = fileServiceClient.uploadImage(tempFile);
        
        // 清理临时文件
        tempFile.delete();
        
        return ResponseEntity.ok(response);
    }
}
```

### 文件服务类

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    
    private final FileServiceClient fileServiceClient;
    
    /**
     * 上传用户头像
     */
    public String uploadAvatar(MultipartFile file) {
        try {
            File tempFile = convertToFile(file);
            FileUploadResponse response = fileServiceClient.uploadImage(tempFile);
            tempFile.delete();
            
            log.info("头像上传成功: {}", response.getFileId());
            return response.getUrl();
            
        } catch (IOException e) {
            log.error("文件处理失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }
    
    /**
     * 上传文档（私有）
     */
    public FileUploadResponse uploadDocument(MultipartFile file) {
        try {
            File tempFile = convertToFile(file);
            FileUploadResponse response = fileServiceClient.uploadFile(
                    tempFile, AccessLevel.PRIVATE);
            tempFile.delete();
            
            return response;
            
        } catch (IOException e) {
            throw new RuntimeException("文档上传失败", e);
        }
    }
    
    /**
     * 获取文件信息
     */
    public FileDetailResponse getFileInfo(String fileId) {
        return fileServiceClient.getFileDetail(fileId);
    }
    
    /**
     * 删除文件
     */
    public void deleteFile(String fileId) {
        fileServiceClient.deleteFile(fileId);
    }
    
    private File convertToFile(MultipartFile multipartFile) throws IOException {
        File tempFile = File.createTempFile("upload-", multipartFile.getOriginalFilename());
        multipartFile.transferTo(tempFile);
        return tempFile;
    }
}
```

### 大文件分片上传

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LargeFileService {
    
    private final FileServiceClient fileServiceClient;
    
    public FileUploadResponse uploadLargeFile(File file) throws IOException {
        long chunkSize = 5 * 1024 * 1024; // 5MB
        
        // 初始化分片上传
        MultipartInitRequest initRequest = MultipartInitRequest.builder()
                .fileName(file.getName())
                .fileSize(file.length())
                .contentType(Files.probeContentType(file.toPath()))
                .chunkSize(chunkSize)
                .build();
        
        MultipartInitResponse initResponse = 
                fileServiceClient.initMultipartUpload(initRequest);
        
        log.info("开始分片上传，任务ID: {}, 总分片数: {}", 
                initResponse.getTaskId(), initResponse.getTotalChunks());
        
        try {
            // 上传各个分片
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[(int) chunkSize];
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
                    
                    log.info("分片 {} 上传完成", partNumber - 1);
                }
            }
            
            // 完成上传
            String fileHash = calculateMD5(file);
            FileUploadResponse response = fileServiceClient.completeMultipartUpload(
                    initResponse.getTaskId(), fileHash);
            
            log.info("分片上传完成，文件ID: {}", response.getFileId());
            return response;
            
        } catch (Exception e) {
            // 上传失败，取消任务
            log.error("分片上传失败，取消任务", e);
            fileServiceClient.cancelMultipartUpload(initResponse.getTaskId());
            throw e;
        }
    }
    
    private String calculateMD5(File file) throws IOException {
        // MD5 计算实现
        // ...
        return "file-hash";
    }
}
```

## 多环境配置

### 开发环境

```yaml
# application-dev.yml
file-service:
  client:
    server-url: http://localhost:8089
    tenant-id: blog-dev
    token: dev-token
```

### 测试环境

```yaml
# application-test.yml
file-service:
  client:
    server-url: https://file-service-test.example.com
    tenant-id: blog-test
    token: ${FILE_SERVICE_TOKEN}
```

### 生产环境

```yaml
# application-prod.yml
file-service:
  client:
    server-url: https://file-service.example.com
    tenant-id: blog
    token: ${FILE_SERVICE_TOKEN}
    custom-domain: https://files.example.com
    cdn-domain: https://cdn.example.com
    max-connections: 100
    connect-timeout: 15000
    read-timeout: 60000
```

## 健康检查

可以添加健康检查端点来监控 File Service 连接状态：

```java
@Component
public class FileServiceHealthIndicator implements HealthIndicator {
    
    private final FileServiceClient fileServiceClient;
    
    public FileServiceHealthIndicator(FileServiceClient fileServiceClient) {
        this.fileServiceClient = fileServiceClient;
    }
    
    @Override
    public Health health() {
        try {
            // 尝试获取一个测试文件的信息
            // 如果成功，说明服务可用
            // 这里可以根据实际情况调整检查逻辑
            return Health.up()
                    .withDetail("service", "File Service")
                    .withDetail("status", "UP")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("service", "File Service")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

## 日志配置

在 `application.yml` 中配置日志级别：

```yaml
logging:
  level:
    com.platform.fileservice.client: DEBUG
    com.platform.fileservice.starter: DEBUG
```

或使用 Logback 配置：

```xml
<configuration>
    <logger name="com.platform.fileservice.client" level="DEBUG"/>
    <logger name="com.platform.fileservice.starter" level="DEBUG"/>
</configuration>
```

## 故障排查

### 问题 1: Bean 未自动配置

**症状**: 无法注入 `FileServiceClient`

**解决方案**:
1. 确认已添加 starter 依赖
2. 确认配置了必需的属性（`server-url` 和 `tenant-id`）
3. 检查 Spring Boot 版本是否兼容（需要 3.x）

### 问题 2: 认证失败

**症状**: 抛出 `AuthenticationException`

**解决方案**:
1. 检查 `token` 配置是否正确
2. 如果使用 Spring Security，确认 Security Context 中有有效令牌
3. 检查令牌是否过期

### 问题 3: 连接超时

**症状**: 抛出 `NetworkException`

**解决方案**:
1. 检查 `server-url` 配置是否正确
2. 确认 File Service 服务是否运行
3. 增加 `connect-timeout` 和 `read-timeout` 值

### 问题 4: 自定义 Bean 未生效

**症状**: 自定义的 `TokenProvider` 或 `FileServiceClient` Bean 未被使用

**解决方案**:
- 确认自定义 Bean 的配置类被 Spring 扫描到
- 检查是否有多个相同类型的 Bean 定义

## 自动配置详情

### 配置类

`FileServiceAutoConfiguration` 提供以下自动配置：

1. **FileServiceProperties**: 绑定 `file-service.client` 前缀的配置属性
2. **TokenProvider**: 默认实现，支持静态令牌和 Spring Security 集成
3. **FileServiceClient**: 客户端实例，使用配置属性初始化

### 条件装配

- `@ConditionalOnClass(FileServiceClient.class)`: 只有当 `file-service-client` 在 classpath 中时才启用
- `@ConditionalOnMissingBean`: 允许用户提供自定义 Bean 覆盖默认配置

### 配置优先级

1. 用户自定义的 `FileServiceClient` Bean（最高优先级）
2. 用户自定义的 `TokenProvider` Bean
3. 自动配置的默认 Bean（最低优先级）

## 示例项目

查看完整的示例项目：[file-service-example](../examples/file-service-example)

## 相关文档

- [File Service Client 文档](../file-service-client/README.md)
- [File Service API 文档](../file-service/API.md)

## 许可证

本项目采用 MIT 许可证。
