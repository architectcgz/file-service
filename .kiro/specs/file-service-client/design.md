# Design Document: File Service Client SDK

## Overview

The File Service Client SDK provides a Java client library and Spring Boot starter for integrating with the File Service platform. The design follows the proven pattern established by id-generator-client, providing a clean API for file upload, download, and management operations with automatic tenant isolation and authentication.

## Architecture

### Module Structure

```
file-service/
├── file-service-client/              # Core client library
│   ├── src/main/java/
│   │   └── com/platform/fileservice/client/
│   │       ├── FileServiceClient.java           # Main client interface
│   │       ├── FileServiceClientImpl.java       # HTTP-based implementation
│   │       ├── config/
│   │       │   ├── FileServiceClientConfig.java # Configuration class
│   │       │   └── TokenProvider.java           # Token provider interface
│   │       ├── model/
│   │       │   ├── FileUploadRequest.java
│   │       │   ├── FileUploadResponse.java
│   │       │   ├── FileDetailResponse.java
│   │       │   ├── MultipartInitRequest.java
│   │       │   ├── MultipartInitResponse.java
│   │       │   ├── MultipartUploadPart.java
│   │       │   ├── InstantUploadCheckRequest.java
│   │       │   ├── InstantUploadCheckResponse.java
│   │       │   ├── AccessLevel.java             # Enum: PUBLIC, PRIVATE
│   │       │   └── ApiResponse.java             # Generic response wrapper
│   │       └── exception/
│   │           ├── FileServiceException.java    # Base exception
│   │           ├── InvalidRequestException.java
│   │           ├── AuthenticationException.java
│   │           ├── AccessDeniedException.java
│   │           ├── FileNotFoundException.java
│   │           ├── QuotaExceededException.java
│   │           ├── NetworkException.java
│   │           └── ParseException.java
│   └── pom.xml
│
└── file-service-spring-boot-starter/  # Spring Boot auto-configuration
    ├── src/main/java/
    │   └── com/platform/fileservice/starter/
    │       ├── FileServiceAutoConfiguration.java
    │       ├── FileServiceProperties.java
    │       └── SpringTokenProvider.java         # Spring Security integration
    ├── src/main/resources/
    │   └── META-INF/
    │       └── spring.factories                 # Auto-configuration registration
    └── pom.xml
```

### Layered Architecture

```
┌─────────────────────────────────────────┐
│     Application Layer                   │
│  (Spring Boot / Plain Java App)         │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│   File Service Client Interface         │
│   - uploadImage()                        │
│   - uploadFile()                         │
│   - getFileUrl()                         │
│   - deleteFile()                         │
│   - multipartUpload()                    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│   HTTP Client Implementation             │
│   - Request building                     │
│   - Response parsing                     │
│   - Error handling                       │
│   - Connection pooling                   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│   File Service REST API                  │
│   (Backend Service)                      │
└──────────────────────────────────────────┘
```

## Components and Interfaces

### 1. FileServiceClient Interface

The main client interface providing all file operations.

```java
public interface FileServiceClient extends AutoCloseable {
    
    // Image upload
    FileUploadResponse uploadImage(File imageFile) throws FileServiceException;
    FileUploadResponse uploadImage(InputStream inputStream, String fileName, 
                                   long fileSize) throws FileServiceException;
    
    // File upload
    FileUploadResponse uploadFile(File file) throws FileServiceException;
    FileUploadResponse uploadFile(InputStream inputStream, String fileName, 
                                  long fileSize, String contentType) throws FileServiceException;
    
    // File upload with access level
    FileUploadResponse uploadFile(File file, AccessLevel accessLevel) throws FileServiceException;
    
    // File access
    String getFileUrl(String fileId) throws FileServiceException;
    FileDetailResponse getFileDetail(String fileId) throws FileServiceException;
    
    // File deletion
    void deleteFile(String fileId) throws FileServiceException;
    
    // Multipart upload
    MultipartInitResponse initMultipartUpload(MultipartInitRequest request) throws FileServiceException;
    MultipartUploadPart uploadPart(String taskId, int partNumber, 
                                   InputStream data, long size) throws FileServiceException;
    FileUploadResponse completeMultipartUpload(String taskId, String fileHash) throws FileServiceException;
    void cancelMultipartUpload(String taskId) throws FileServiceException;
    
    // Instant upload (deduplication)
    InstantUploadCheckResponse checkInstantUpload(InstantUploadCheckRequest request) throws FileServiceException;
    
    // Presigned URL
    PresignedUploadResponse getPresignedUploadUrl(PresignedUploadRequest request) throws FileServiceException;
    FileUploadResponse confirmPresignedUpload(String fileId, String fileHash) throws FileServiceException;
    
    // Resource management
    @Override
    void close();
}
```

### 2. FileServiceClientConfig

Configuration class for client initialization.

```java
@Data
@Builder
public class FileServiceClientConfig {
    
    // Required
    private String serverUrl;           // File Service base URL
    private String tenantId;            // Application ID (X-App-Id)
    private TokenProvider tokenProvider; // Authentication token provider
    
    // Optional - Connection settings
    @Builder.Default
    private int connectTimeout = 10000;  // 10 seconds
    @Builder.Default
    private int readTimeout = 30000;     // 30 seconds
    @Builder.Default
    private int maxConnections = 50;
    
    // Optional - Domain configuration
    private String customDomain;         // Custom domain for file URLs
    private String cdnDomain;            // CDN domain for public files
    
    // Optional - Retry settings
    @Builder.Default
    private int maxRetries = 3;
    @Builder.Default
    private long retryDelayMs = 1000;
    
    // Validation
    public void validate() {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (tokenProvider == null) {
            throw new IllegalArgumentException("tokenProvider is required");
        }
        // Validate tenantId format
        if (!tenantId.matches("^[a-z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid tenantId format");
        }
    }
}
```

### 3. TokenProvider Interface

Interface for providing authentication tokens.

```java
@FunctionalInterface
public interface TokenProvider {
    /**
     * Get current authentication token
     * @return JWT token (without "Bearer " prefix)
     */
    String getToken();
    
    // Static factory methods
    static TokenProvider fixed(String token) {
        return () -> token;
    }
    
    static TokenProvider fromSupplier(Supplier<String> supplier) {
        return supplier::get;
    }
}
```

### 4. FileServiceClientImpl

HTTP-based implementation of the client interface.

**Key responsibilities:**
- Build HTTP requests with proper headers (X-App-Id, Authorization)
- Execute HTTP requests using connection pool
- Parse JSON responses into typed objects
- Handle HTTP errors and convert to typed exceptions
- Apply domain/CDN URL replacement for file URLs
- Implement retry logic for transient failures
- Manage HTTP client lifecycle

**HTTP Client:**
- Use `java.net.http.HttpClient` (Java 11+)
- Configure connection pool with max connections
- Set connect and read timeouts
- Enable HTTP/2 support

**Request Headers:**
```
X-App-Id: {tenantId}
Authorization: Bearer {token}
Content-Type: multipart/form-data | application/json
```

**URL Replacement Logic:**
```java
private String replaceFileUrl(String originalUrl, AccessLevel accessLevel) {
    if (originalUrl == null) return null;
    
    // For public files, use CDN domain if configured
    if (accessLevel == AccessLevel.PUBLIC && config.getCdnDomain() != null) {
        return replaceUrlDomain(originalUrl, config.getCdnDomain());
    }
    
    // Otherwise use custom domain if configured
    if (config.getCustomDomain() != null) {
        return replaceUrlDomain(originalUrl, config.getCustomDomain());
    }
    
    return originalUrl;
}

private String replaceUrlDomain(String url, String newDomain) {
    try {
        URI uri = new URI(url);
        return newDomain + uri.getPath() + 
               (uri.getQuery() != null ? "?" + uri.getQuery() : "");
    } catch (URISyntaxException e) {
        return url; // Return original if parsing fails
    }
}
```

### 5. Spring Boot Auto-Configuration

**FileServiceProperties:**
```java
@ConfigurationProperties(prefix = "file-service.client")
@Data
public class FileServiceProperties {
    private String serverUrl;
    private String tenantId;
    private String token;  // Static token (optional)
    
    private int connectTimeout = 10000;
    private int readTimeout = 30000;
    private int maxConnections = 50;
    
    private String customDomain;
    private String cdnDomain;
    
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
}
```

**FileServiceAutoConfiguration:**
```java
@Configuration
@ConditionalOnClass(FileServiceClient.class)
@EnableConfigurationProperties(FileServiceProperties.class)
public class FileServiceAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public TokenProvider tokenProvider(FileServiceProperties properties) {
        // Try to get token from Spring Security context first
        // Fall back to static token from properties
        return new SpringTokenProvider(properties.getToken());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public FileServiceClient fileServiceClient(
            FileServiceProperties properties,
            TokenProvider tokenProvider) {
        
        FileServiceClientConfig config = FileServiceClientConfig.builder()
            .serverUrl(properties.getServerUrl())
            .tenantId(properties.getTenantId())
            .tokenProvider(tokenProvider)
            .connectTimeout(properties.getConnectTimeout())
            .readTimeout(properties.getReadTimeout())
            .maxConnections(properties.getMaxConnections())
            .customDomain(properties.getCustomDomain())
            .cdnDomain(properties.getCdnDomain())
            .maxRetries(properties.getMaxRetries())
            .retryDelayMs(properties.getRetryDelayMs())
            .build();
        
        return new FileServiceClientImpl(config);
    }
}
```

**Application Properties Example:**
```yaml
file-service:
  client:
    server-url: http://localhost:8089
    tenant-id: blog
    token: ${JWT_TOKEN}  # Or use Spring Security
    custom-domain: https://files.example.com
    cdn-domain: https://cdn.example.com
    connect-timeout: 10000
    read-timeout: 30000
    max-connections: 50
```

## Data Models

### Request Models

**FileUploadRequest:**
```java
@Data
@Builder
public class FileUploadRequest {
    private File file;
    private InputStream inputStream;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private AccessLevel accessLevel;
}
```

**MultipartInitRequest:**
```java
@Data
@Builder
public class MultipartInitRequest {
    private String fileName;
    private long fileSize;
    private String contentType;
    private long chunkSize;
    private AccessLevel accessLevel;
}
```

**InstantUploadCheckRequest:**
```java
@Data
@Builder
public class InstantUploadCheckRequest {
    private String fileHash;  // MD5
    private String fileName;
    private long fileSize;
    private String contentType;
    private AccessLevel accessLevel;
}
```

### Response Models

**FileUploadResponse:**
```java
@Data
public class FileUploadResponse {
    private String fileId;
    private String url;
    private String originalName;
    private long fileSize;
    private String contentType;
    private AccessLevel accessLevel;
}
```

**FileDetailResponse:**
```java
@Data
public class FileDetailResponse {
    private String fileId;
    private String originalName;
    private long fileSize;
    private String contentType;
    private String url;
    private LocalDateTime createdAt;
    private String status;
    private AccessLevel accessLevel;
}
```

**MultipartInitResponse:**
```java
@Data
public class MultipartInitResponse {
    private String taskId;
    private String uploadId;
    private int totalChunks;
    private long chunkSize;
}
```

**MultipartUploadPart:**
```java
@Data
public class MultipartUploadPart {
    private int partNumber;
    private String etag;
}
```

**InstantUploadCheckResponse:**
```java
@Data
public class InstantUploadCheckResponse {
    private boolean exists;
    private String fileId;
    private String url;
}
```

**ApiResponse<T>:**
```java
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    
    public boolean isSuccess() {
        return code == 200;
    }
}
```

## Error Handling

### Exception Hierarchy

```
FileServiceException (RuntimeException)
├── InvalidRequestException (400)
├── AuthenticationException (401)
├── AccessDeniedException (403)
├── FileNotFoundException (404)
├── QuotaExceededException (413)
├── NetworkException (network errors)
└── ParseException (JSON parsing errors)
```

### Error Mapping

```java
private void handleErrorResponse(int statusCode, String responseBody) {
    ApiResponse<?> errorResponse = parseErrorResponse(responseBody);
    String message = errorResponse != null ? errorResponse.getMessage() : "Unknown error";
    
    switch (statusCode) {
        case 400 -> throw new InvalidRequestException(message);
        case 401 -> throw new AuthenticationException(message);
        case 403 -> throw new AccessDeniedException(message);
        case 404 -> throw new FileNotFoundException(message);
        case 413 -> throw new QuotaExceededException(message);
        default -> throw new FileServiceException("HTTP " + statusCode + ": " + message);
    }
}
```

## Testing Strategy

The testing strategy combines unit tests for specific scenarios and property-based tests for universal correctness properties.

### Unit Tests
- Test specific upload scenarios (image, file, multipart)
- Test error handling for each exception type
- Test configuration validation
- Test URL replacement logic
- Test Spring Boot auto-configuration
- Mock HTTP responses for predictable testing

### Property-Based Tests
- Test universal properties across randomized inputs
- Use jqwik for property-based testing
- Minimum 100 iterations per property test
- Each test references its design document property


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, I identified the following testable properties and performed reflection to eliminate redundancy:

**Redundancy Analysis:**
- Properties 1.3 and 11.1/11.2 all test that X-App-Id header is included → Combined into Property 1
- Properties 8.1-8.5 all test HTTP error code mapping → Combined into Property 2
- Properties 9.2, 9.3, 9.4 all test response structure → Combined into Property 3
- Properties 15.3, 15.4, 15.5 all test URL replacement → Combined into Property 6

### Property 1: Tenant ID Header Inclusion

*For any* file operation request (upload, download, delete, multipart), the HTTP request SHALL include the X-App-Id header with the value matching the configured tenant ID.

**Validates: Requirements 1.3, 11.1, 11.2**

### Property 2: HTTP Error Code Mapping

*For any* HTTP error response (400, 401, 403, 404, 413), the client SHALL throw the corresponding typed exception (InvalidRequestException, AuthenticationException, AccessDeniedException, FileNotFoundException, QuotaExceededException) with the error message from the server response.

**Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.7**

### Property 3: Response Structure Completeness

*For any* successful API response (FileUploadResponse, FileDetailResponse, MultipartInitResponse), the parsed response object SHALL contain all required fields as specified in the API contract (non-null values for mandatory fields).

**Validates: Requirements 3.4, 9.2, 9.3, 9.4**

### Property 4: Configuration Validation

*For any* invalid configuration (missing serverUrl, missing tenantId, invalid tenantId format, missing tokenProvider), the client initialization SHALL throw an exception with a descriptive error message.

**Validates: Requirements 6.6, 11.3, 12.4**

### Property 5: JSON Parsing Robustness

*For any* invalid JSON response from the server, the client SHALL throw ParseException with details about the parsing failure.

**Validates: Requirements 9.5**

### Property 6: URL Domain Replacement

*For any* file URL returned by the server, when a custom domain or CDN domain is configured, the client SHALL replace the domain portion while preserving the path and query parameters exactly.

**Validates: Requirements 15.3, 15.4, 15.5**

**Sub-property 6a:** When CDN domain is configured and access level is PUBLIC, use CDN domain.
**Sub-property 6b:** When custom domain is configured and CDN domain is not applicable, use custom domain.
**Sub-property 6c:** Path and query parameters SHALL remain unchanged after domain replacement.

### Property 7: Authentication Token Inclusion

*For any* API request, the HTTP request SHALL include the Authorization header with the Bearer token obtained from the configured TokenProvider.

**Validates: Requirements 1.4, 12.2**

### Property 8: Access Level Preservation

*For any* file upload request with a specified access level (PUBLIC or PRIVATE), the access level SHALL be included in the request and preserved in the response.

**Validates: Requirements 2.3**

### Property 9: Multipart Chunk Metadata

*For any* multipart upload chunk, the upload response SHALL contain the correct part number and a non-empty ETag value.

**Validates: Requirements 5.5**

### Property 10: Instant Upload Request Structure

*For any* instant upload check request, the request SHALL include all required fields: fileHash, fileName, fileSize, and contentType.

**Validates: Requirements 13.2**

### Property 11: Sensitive Data Exclusion from Logs

*For any* log message produced by the client, the message SHALL NOT contain authentication tokens or file content data.

**Validates: Requirements 14.5**

### Property 12: Small File Direct Upload

*For any* file smaller than 5MB, the direct upload method SHALL successfully upload the file and return a valid FileUploadResponse.

**Validates: Requirements 2.1**

