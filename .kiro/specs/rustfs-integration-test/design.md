# Design Document: RustFS Integration Test

## Overview

本设计文档描述了 RustFS 集成测试的实现方案。该测试将验证文件上传服务与 RustFS 对象存储的完整集成，包括文件上传、存储验证、URL 访问、以及文件删除等核心功能。

测试将使用真实的 RustFS 连接（非 Mock），通过 Spring Boot 测试框架和 MockMvc 来模拟 HTTP 请求，验证整个上传流程的正确性。

## Architecture

### 测试架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Integration Test                          │
│  ┌──────────────┐                                           │
│  │  Test Class  │                                           │
│  │  (JUnit 5)   │                                           │
│  └──────┬───────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────┐      ┌─────────────────┐                │
│  │   MockMvc    │─────▶│  Controllers    │                │
│  └──────────────┘      └────────┬────────┘                │
│                                  │                          │
│                                  ▼                          │
│                         ┌────────────────┐                 │
│                         │   Services     │                 │
│                         └────────┬───────┘                 │
│                                  │                          │
│                                  ▼                          │
│                         ┌────────────────┐                 │
│                         │  Repositories  │                 │
│                         └────────┬───────┘                 │
│                                  │                          │
└──────────────────────────────────┼──────────────────────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │ S3StorageService│
                          └────────┬────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  RustFS/MinIO   │
                          │  (S3 Compatible)│
                          └─────────────────┘
```

### 测试层次

1. **HTTP 层**: 使用 MockMvc 模拟 HTTP 请求
2. **应用层**: 测试 Controllers 和 Services
3. **存储层**: 真实的 S3StorageService 连接到 RustFS
4. **验证层**: 直接使用 S3Client 验证文件存储状态

## Components and Interfaces

### 1. RustFSIntegrationTest (主测试类)

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("rustfs-test")
@TestPropertySource(properties = {
    "storage.type=s3",
    "storage.s3.endpoint=http://localhost:9100",
    "storage.s3.access-key=admin",
    "storage.s3.secret-key=admin123456",
    "storage.s3.bucket=test-bucket",
    "storage.s3.region=us-east-1",
    "storage.s3.path-style-access=true"
})
@Transactional
class RustFSIntegrationTest {
    // 测试方法
}
```

**职责**:
- 配置测试环境（使用 S3 存储）
- 执行集成测试用例
- 验证上传、访问、删除功能

### 2. RustFSTestConfig (测试配置类)

```java
@TestConfiguration
@Profile("rustfs-test")
public class RustFSTestConfig {
    @Bean
    public S3Client testS3Client(S3Properties properties);
    
    @Bean
    public HttpClient httpClient();
}
```

**职责**:
- 提供测试专用的 S3Client（用于直接验证）
- 提供 HttpClient（用于 URL 访问测试）
- 不 Mock S3StorageService，使用真实实现

### 3. 测试辅助类

#### FileTestData
```java
public class FileTestData {
    public static MockMultipartFile createTextFile(String filename, String content);
    public static MockMultipartFile createImageFile(String filename);
    public static MockMultipartFile createLargeFile(String filename, long sizeInMB);
    public static byte[] generateRandomBytes(int size);
}
```

#### S3Verifier
```java
public class S3Verifier {
    private final S3Client s3Client;
    private final String bucket;
    
    public boolean fileExists(String path);
    public byte[] getFileContent(String path);
    public long getFileSize(String path);
    public String getContentType(String path);
}
```

#### URLAccessVerifier
```java
public class URLAccessVerifier {
    private final HttpClient httpClient;
    
    public byte[] downloadFile(String url);
    public int getStatusCode(String url);
    public boolean isAccessible(String url);
}
```

## Data Models

### 测试数据结构

```java
// 测试文件信息
public class TestFileInfo {
    private String fileId;
    private String filename;
    private String contentType;
    private long size;
    private byte[] content;
    private String url;
    private String storagePath;
}

// 测试上下文
public class TestContext {
    private String appId;
    private Long userId;
    private List<TestFileInfo> uploadedFiles;
    
    public void addUploadedFile(TestFileInfo file);
    public void cleanup(); // 清理测试数据
}
```

## Correctness Properties

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的形式化陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: 上传后文件存在性
*For any* 成功上传的文件，该文件应该在 RustFS 中的指定路径存在，并且文件内容与上传的内容完全一致。

**Validates: Requirements 1.1, 1.3, 1.4**

### Property 2: URL 访问一致性
*For any* 成功上传的文件，通过返回的 URL 访问应该能够获取到文件内容，且内容与原始上传内容完全一致。

**Validates: Requirements 2.1, 2.2, 2.3**

### Property 3: 文件类型保持性
*For any* 上传的文件，存储在 RustFS 中的文件的 Content-Type 应该与上传时指定的 Content-Type 一致。

**Validates: Requirements 3.4**

### Property 4: 分片上传完整性
*For any* 通过分片上传的大文件，完成后的文件内容应该与原始文件内容完全一致，不应有任何数据丢失或损坏。

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

### Property 5: 删除后不可访问性
*For any* 被删除的文件，该文件应该从 RustFS 中移除，通过原 URL 访问应该返回错误（404 或其他错误状态）。

**Validates: Requirements 5.1, 5.2, 5.3**

### Property 6: 引用计数删除正确性
*For any* 被多个文件记录引用的存储对象，只有当引用计数降为 0 时，该存储对象才应该从 RustFS 中删除。

**Validates: Requirements 5.4**

## Error Handling

### 1. RustFS 连接错误
- **场景**: RustFS 服务不可用
- **处理**: 测试应该跳过（使用 @EnabledIf 条件）或明确失败并提供诊断信息
- **验证**: 在 @BeforeAll 中检查 RustFS 连接

### 2. 上传失败
- **场景**: 网络错误、权限错误、存储空间不足
- **处理**: 验证异常类型和错误消息
- **验证**: 确保数据库中没有创建文件记录

### 3. URL 访问失败
- **场景**: 文件不存在、权限不足、URL 过期
- **处理**: 验证返回的 HTTP 状态码
- **验证**: 确保错误响应包含有用的诊断信息

### 4. 测试清理失败
- **场景**: 测试后清理文件失败
- **处理**: 记录警告日志，不影响测试结果
- **验证**: 使用 @AfterEach 确保清理执行

## Testing Strategy

### 单元测试 vs 集成测试

本设计专注于**集成测试**，验证多个组件协同工作的正确性。单元测试已经在其他测试类中覆盖（如 S3StorageServiceTest）。

### 测试用例设计

#### 1. 基础上传测试

**Test: 文本文件上传和访问**
```java
@Test
@DisplayName("Upload text file to RustFS and verify access via URL")
void uploadTextFile_shouldStoreInRustFSAndBeAccessible()
```
- 上传一个文本文件
- 验证返回的 fileId 和 URL
- 使用 S3Client 验证文件存在
- 通过 URL 下载文件并验证内容
- **Validates: Property 1, Property 2**

**Test: 图片文件上传和访问**
```java
@Test
@DisplayName("Upload image file to RustFS and verify content type")
void uploadImageFile_shouldPreserveContentType()
```
- 上传一个图片文件（JPEG）
- 验证 Content-Type 正确
- 验证文件内容完整
- **Validates: Property 1, Property 3**

**Test: 二进制文件上传和访问**
```java
@Test
@DisplayName("Upload binary file to RustFS and verify integrity")
void uploadBinaryFile_shouldMaintainIntegrity()
```
- 上传一个二进制文件
- 验证文件内容字节级一致
- **Validates: Property 1, Property 2**

#### 2. 大文件分片上传测试

**Test: 大文件分片上传**
```java
@Test
@DisplayName("Upload large file using multipart upload")
void uploadLargeFile_shouldUseMultipartUpload()
```
- 创建一个超过阈值的大文件（15MB）
- 验证使用了分片上传
- 验证所有分片都上传成功
- 验证完成后文件内容完整
- **Validates: Property 4**

**Test: 分片上传进度查询**
```java
@Test
@DisplayName("Query multipart upload progress")
void queryUploadProgress_shouldReturnCorrectStatus()
```
- 初始化分片上传
- 上传部分分片
- 查询上传进度
- 验证进度信息正确
- **Validates: Requirements 4.1, 4.2**

#### 3. 文件删除测试

**Test: 单文件删除**
```java
@Test
@DisplayName("Delete file should remove from RustFS")
void deleteFile_shouldRemoveFromRustFS()
```
- 上传文件
- 删除文件
- 验证文件从 RustFS 中移除
- 验证 URL 不可访问
- **Validates: Property 5**

**Test: 引用计数删除**
```java
@Test
@DisplayName("Delete file with reference count should only remove when count reaches zero")
void deleteFileWithReferences_shouldOnlyRemoveWhenCountZero()
```
- 上传相同内容的文件两次（去重）
- 删除第一个文件记录
- 验证存储对象仍然存在
- 删除第二个文件记录
- 验证存储对象被删除
- **Validates: Property 6**

#### 4. 多文件类型测试

**Test: 多种文件类型**
```java
@Test
@DisplayName("Upload multiple file types and verify all work correctly")
void uploadMultipleFileTypes_shouldAllWork()
```
- 上传文本、图片、视频等多种类型
- 验证每种类型都能正确存储和访问
- **Validates: Requirements 3.1, 3.2, 3.3**

#### 5. 错误场景测试

**Test: RustFS 不可用**
```java
@Test
@DisplayName("Upload should fail gracefully when RustFS is unavailable")
@EnabledIf("isRustFSAvailable")
void uploadWhenRustFSDown_shouldFailGracefully()
```
- 模拟 RustFS 不可用（通过错误的 endpoint）
- 验证返回清晰的错误消息
- **Validates: Requirements 6.1**

**Test: 无效的 bucket**
```java
@Test
@DisplayName("Upload to non-existent bucket should handle error")
void uploadToNonExistentBucket_shouldHandleError()
```
- 配置不存在的 bucket
- 验证错误处理
- **Validates: Requirements 6.2**

#### 6. 性能测试（可选）

**Test: 上传性能基准**
```java
@Test
@Tag("performance")
@DisplayName("Measure upload performance for different file sizes")
void measureUploadPerformance()
```
- 测试不同大小文件的上传时间
- 记录性能指标
- 验证性能满足阈值
- **Validates: Requirements 8.1, 8.4**

### 测试配置

#### application-rustfs-test.yml
```yaml
storage:
  type: s3
  s3:
    endpoint: http://localhost:9100
    access-key: admin
    secret-key: admin123456
    bucket: test-bucket
    region: us-east-1
    path-style-access: true
  multipart:
    threshold: 10485760  # 10MB
    part-size: 5242880   # 5MB
```

### 测试数据清理

```java
@AfterEach
void cleanup() {
    // 清理测试上传的文件
    testContext.getUploadedFiles().forEach(file -> {
        try {
            // 删除文件记录
            mockMvc.perform(delete("/api/v1/upload/" + file.getFileId())
                .header("X-App-Id", testContext.getAppId())
                .header("X-User-Id", testContext.getUserId()));
        } catch (Exception e) {
            log.warn("Failed to cleanup test file: {}", file.getFileId(), e);
        }
    });
    
    testContext.clear();
}
```

### 测试执行条件

```java
@BeforeAll
static void checkRustFSAvailability() {
    // 检查 RustFS 是否可用
    boolean available = checkRustFSConnection();
    Assumptions.assumeTrue(available, "RustFS is not available, skipping integration tests");
}

private static boolean checkRustFSConnection() {
    try {
        S3Client client = buildTestS3Client();
        client.listBuckets();
        return true;
    } catch (Exception e) {
        log.warn("RustFS connection check failed: {}", e.getMessage());
        return false;
    }
}
```

## Implementation Notes

### 1. 真实 S3 连接 vs Mock

- **不使用 Mock**: 测试使用真实的 S3StorageService 连接到 RustFS
- **原因**: 集成测试的目的是验证真实环境下的行为
- **配置**: 通过 @TestPropertySource 覆盖配置

### 2. 测试隔离

- **数据库**: 使用 @Transactional 自动回滚
- **文件存储**: 使用唯一的测试 bucket 或路径前缀
- **清理**: @AfterEach 确保测试文件被删除

### 3. 测试可靠性

- **条件执行**: 使用 @EnabledIf 检查 RustFS 可用性
- **重试机制**: 对于网络相关的验证，考虑重试
- **超时设置**: 为 HTTP 请求设置合理的超时

### 4. 性能考虑

- **并行执行**: 测试可以并行执行（使用不同的文件名）
- **大文件测试**: 标记为 @Tag("slow") 或 @Tag("performance")
- **CI/CD**: 可以配置跳过性能测试

### 5. Docker 集成

测试可以使用 Docker Compose 启动的 RustFS：

```yaml
# docker-compose.test.yml
services:
  rustfs-test:
    image: minio/minio:latest
    ports:
      - "9100:9000"
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: admin123456
    command: server /data
```

启动测试环境：
```bash
docker-compose -f docker-compose.test.yml up -d
mvn test -Dtest=RustFSIntegrationTest
docker-compose -f docker-compose.test.yml down
```

## Dependencies

### Maven Dependencies

```xml
<!-- AWS SDK for S3 (already included) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>

<!-- HTTP Client for URL access testing -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ for fluent assertions (already included) -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

## Test Execution

### 本地执行

```bash
# 启动 RustFS (Docker)
cd docker
docker-compose up -d minio

# 运行集成测试
cd ../file-service
mvn test -Dtest=RustFSIntegrationTest

# 停止 RustFS
cd ../docker
docker-compose down
```

### CI/CD 执行

```yaml
# .github/workflows/integration-test.yml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Start RustFS
        run: |
          cd docker
          docker-compose up -d minio
          sleep 10  # Wait for RustFS to be ready
      
      - name: Run Integration Tests
        run: |
          cd file-service
          mvn test -Dtest=RustFSIntegrationTest
      
      - name: Stop RustFS
        run: |
          cd docker
          docker-compose down
```

## Success Criteria

测试成功的标准：

1. ✅ 所有测试用例通过
2. ✅ 文件能够成功上传到 RustFS
3. ✅ 上传的文件可以通过 URL 访问
4. ✅ 文件内容与原始内容完全一致
5. ✅ 文件删除后从 RustFS 中移除
6. ✅ 错误场景得到正确处理
7. ✅ 测试数据被正确清理
8. ✅ 测试可以在本地和 CI/CD 环境中运行
