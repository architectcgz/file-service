# 设计文档

## 概述

本设计文档描述文件服务模块化优化的技术实现方案。该优化旨在解决三个核心问题：

1. **私有存储桶访问问题**：通过实施双 Bucket 策略，将公开文件和私有文件分别存储在不同的存储桶中，公开文件可直接访问，私有文件通过预签名 URL 访问
2. **缺少文件管理功能**：提供管理员 API，支持文件查询、过滤、删除和统计功能
3. **缺少租户管理功能**：实现租户管理系统，支持配额控制、使用统计和租户生命周期管理

该设计采用模块化单体架构，在保持部署简单的同时实现功能扩展。

## 架构

### 系统架构

系统采用分层架构，包含以下层次：

```
┌─────────────────────────────────────────────────────────┐
│                    Interfaces Layer                      │
│  (Controllers: File, Multipart, Presigned, Admin)       │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                      │
│  (Services: Upload, InstantUpload, Multipart,           │
│   FileAccess, TenantManagement, FileManagement)         │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                     Domain Layer                         │
│  (Models: FileRecord, Tenant, TenantUsage, AuditLog)    │
│  (Repositories: FileRecord, Tenant, TenantUsage)        │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                     │
│  (Storage: S3StorageService, LocalStorageService)       │
│  (Persistence: MyBatis Mappers, PostgreSQL)             │
└─────────────────────────────────────────────────────────┘
```

### 模块划分

虽然当前实现为单体应用，但代码按照模块化原则组织，为未来可能的拆分做准备：

- **file-core**: 核心文件上传/下载/存储逻辑（现有 file-service 代码）
- **file-admin**: 管理功能模块（新增）
- **file-tenant**: 租户管理模块（新增）

## 组件和接口

### 1. 双 Bucket 存储组件

#### S3Properties 配置扩展

```java
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String publicBucket;   // 新增：公开存储桶
    private String privateBucket;  // 新增：私有存储桶
    private String region;
    private String cdnDomain;      // 可选：CDN 域名
}
```

#### S3StorageService 接口扩展

```java
public interface StorageService {
    // 现有方法
    String uploadFile(InputStream inputStream, String objectKey, String contentType);
    void deleteFile(String objectKey);
    InputStream downloadFile(String objectKey);
    
    // 新增方法
    String uploadToPublicBucket(InputStream inputStream, String objectKey, String contentType);
    String uploadToPrivateBucket(InputStream inputStream, String objectKey, String contentType);
    String generatePresignedUrl(String objectKey, Duration expiration);
    String getPublicUrl(String objectKey);
}
```

#### BucketPolicyUtil 工具类

```java
public class BucketPolicyUtil {
    /**
     * 生成公开桶的访问策略
     */
    public static String generatePublicBucketPolicy(String bucketName) {
        return """
        {
          "Version": "2012-10-17",
          "Statement": [{
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::%s/*"
          }]
        }
        """.formatted(bucketName);
    }
    
    /**
     * 应用桶策略到指定存储桶
     */
    public static void applyBucketPolicy(S3Client s3Client, String bucketName, String policy);
}
```

### 2. 文件访问服务

#### FileAccessService 接口

```java
public interface FileAccessService {
    /**
     * 获取文件访问 URL
     * @param fileId 文件 ID
     * @param requestUserId 请求用户 ID
     * @return 文件访问 URL（公开文件返回直接 URL，私有文件返回预签名 URL）
     */
    String getFileUrl(String fileId, String requestUserId);
    
    /**
     * 验证用户是否有权访问文件
     */
    boolean canAccess(String fileId, String requestUserId);
    
    /**
     * 更新文件访问级别
     */
    void updateAccessLevel(String fileId, AccessLevel newLevel, String requestUserId);
}
```

#### FileAccessService 实现逻辑

```java
public String getFileUrl(String fileId, Long requestUserId) {
    FileRecord file = fileRecordRepository.findById(fileId)
        .orElseThrow(() -> new FileNotFoundException(fileId));
    
    if (file.getAccessLevel() == AccessLevel.PUBLIC) {
        // 公开文件：返回公开 URL
        return s3Properties.getCdnDomain() != null 
            ? buildCdnUrl(file.getStoragePath())
            : storageService.getPublicUrl(file.getStoragePath());
    } else {
        // 私有文件：验证权限后返回预签名 URL
        if (!canAccessFile(file, requestUserId)) {
            throw new AccessDeniedException(fileId, requestUserId);
        }
        return storageService.generatePresignedUrl(
            file.getStoragePath(), 
            Duration.ofHours(presignedUrlExpireHours)
        );
    }
}

/**
 * 检查用户是否有权访问文件（传入已查询的 FileRecord，避免重复查询）
 */
private boolean canAccessFile(FileRecord file, Long requestUserId) {
    // 文件所有者可以访问
    if (file.getUserId().equals(requestUserId)) {
        return true;
    }
    
    // 公开文件任何人都可以访问
    if (file.getAccessLevel() == AccessLevel.PUBLIC) {
        return true;
    }
    
    // 私有文件只有所有者可以访问
    return false;
}
```

### 3. 租户管理组件

#### Tenant 领域模型

```java
public class Tenant {
    private String tenantId;
    private String tenantName;
    private TenantStatus status;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private List<String> allowedFileTypes;
    private String contactEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum TenantStatus {
        ACTIVE, SUSPENDED, DELETED
    }
    
    // 业务方法
    public void suspend() {
        if (this.status == TenantStatus.DELETED) {
            throw new IllegalStateException("Cannot suspend deleted tenant");
        }
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void activate() {
        if (this.status == TenantStatus.DELETED) {
            throw new IllegalStateException("Cannot activate deleted tenant");
        }
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markDeleted() {
        this.status = TenantStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
}
```

#### TenantUsage 领域模型

```java
public class TenantUsage {
    private String tenantId;
    private Long usedStorageBytes;
    private Integer usedFileCount;
    private LocalDateTime lastUploadAt;
    private LocalDateTime updatedAt;
    
    // 业务方法
    public void incrementUsage(long fileSize) {
        this.usedStorageBytes += fileSize;
        this.usedFileCount += 1;
        this.lastUploadAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void decrementUsage(long fileSize) {
        this.usedStorageBytes = Math.max(0, this.usedStorageBytes - fileSize);
        this.usedFileCount = Math.max(0, this.usedFileCount - 1);
        this.updatedAt = LocalDateTime.now();
    }
}
```

#### TenantRepository 接口

```java
public interface TenantRepository {
    Optional<Tenant> findById(String tenantId);
    List<Tenant> findAll(TenantQuery query);
    long count(TenantQuery query);
    Tenant save(Tenant tenant);
    void delete(String tenantId);
}

public interface TenantUsageRepository {
    Optional<TenantUsage> findById(String tenantId);
    TenantUsage save(TenantUsage usage);
    void incrementUsage(String tenantId, long fileSize);
    void decrementUsage(String tenantId, long fileSize);
}
```

#### TenantDomainService

```java
public class TenantDomainService {
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final TenantProperties tenantProperties;
    
    /**
     * 检查租户配额
     */
    public void checkQuota(String tenantId, long fileSize) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        
        // 检查租户状态
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }
        
        TenantUsage usage = tenantUsageRepository.findById(tenantId)
            .orElse(new TenantUsage(tenantId));
        
        // 检查存储空间
        if (usage.getUsedStorageBytes() + fileSize > tenant.getMaxStorageBytes()) {
            throw new QuotaExceededException(
                "Storage quota exceeded: " + 
                (usage.getUsedStorageBytes() + fileSize) + 
                " > " + tenant.getMaxStorageBytes()
            );
        }
        
        // 检查文件数量
        if (usage.getUsedFileCount() >= tenant.getMaxFileCount()) {
            throw new QuotaExceededException(
                "File count limit reached: " + 
                usage.getUsedFileCount() + 
                " >= " + tenant.getMaxFileCount()
            );
        }
        
        // 检查单文件大小
        if (fileSize > tenant.getMaxSingleFileSize()) {
            throw new FileTooLargeException(fileSize, tenant.getMaxSingleFileSize());
        }
    }
    
    /**
     * 获取或创建租户
     */
    public Tenant getOrCreateTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseGet(() -> {
                if (!tenantProperties.isAutoCreate()) {
                    throw new TenantNotFoundException(tenantId);
                }
                return createDefaultTenant(tenantId);
            });
    }
    
    private Tenant createDefaultTenant(String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxStorageBytes(tenantProperties.getDefaultMaxStorageBytes());
        tenant.setMaxFileCount(tenantProperties.getDefaultMaxFileCount());
        tenant.setMaxSingleFileSize(tenantProperties.getDefaultMaxSingleFileSize());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant savedTenant = tenantRepository.save(tenant);
        
        // 同时创建租户使用统计记录
        TenantUsage usage = new TenantUsage(tenantId);
        tenantUsageRepository.save(usage);
        
        return savedTenant;
    }
}
```

### 4. 文件管理组件

#### FileQuery 查询对象

```java
public class FileQuery {
    private String tenantId;
    private Long userId;           // 使用 Long 类型与 FileRecord 保持一致
    private String contentType;
    private AccessLevel accessLevel;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long minSize;
    private Long maxSize;
    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
```

#### FileManagementService 接口

```java
public interface FileManagementService {
    /**
     * 查询文件列表
     */
    PageResponse<FileRecord> listFiles(FileQuery query);
    
    /**
     * 获取文件详情
     */
    FileRecord getFileDetail(String fileId);
    
    /**
     * 删除单个文件
     */
    void deleteFile(String fileId, String adminUserId);
    
    /**
     * 批量删除文件
     */
    BatchDeleteResult batchDeleteFiles(List<String> fileIds, String adminUserId);
    
    /**
     * 获取存储统计
     */
    StorageStatistics getStorageStatistics();
    
    /**
     * 按租户获取存储统计
     */
    Map<String, TenantStorageStats> getStorageStatisticsByTenant();
}
```

#### StorageStatistics 响应对象

```java
public class StorageStatistics {
    private long totalFiles;
    private long totalStorageBytes;
    private long publicFiles;
    private long privateFiles;
    private Map<String, Long> filesByType;      // contentType -> count
    private Map<String, Long> storageByTenant;  // tenantId -> bytes
    private LocalDateTime statisticsTime;
}

public class TenantStorageStats {
    private String tenantId;
    private String tenantName;
    private long fileCount;
    private long storageBytes;
    private long maxStorageBytes;
    private long maxFileCount;
    private double storageUsagePercent;
    private double fileCountUsagePercent;
}

public class BatchDeleteResult {
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<DeleteFailure> failures;
    
    public static class DeleteFailure {
        private String fileId;
        private String reason;
    }
}
```

### 5. 审计日志组件

#### AuditLog 领域模型

```java
public class AuditLog {
    private String id;
    private String adminUserId;
    private AuditAction action;
    private TargetType targetType;
    private String targetId;
    private String tenantId;
    private Map<String, Object> details;
    private String ipAddress;
    private LocalDateTime createdAt;
    
    public enum AuditAction {
        DELETE_FILE,
        BATCH_DELETE_FILES,
        CREATE_TENANT,
        UPDATE_TENANT,
        SUSPEND_TENANT,
        UPDATE_QUOTA
    }
    
    public enum TargetType {
        FILE,
        TENANT
    }
}
```

#### AuditLogService 接口

```java
public interface AuditLogService {
    /**
     * 记录审计日志
     */
    void log(AuditLog auditLog);
    
    /**
     * 查询审计日志
     */
    PageResponse<AuditLog> queryLogs(AuditLogQuery query);
}
```

### 6. 管理员 API 控制器

#### TenantAdminController

```java
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantAdminController {
    
    @PostMapping
    public ApiResponse<Tenant> createTenant(@RequestBody CreateTenantRequest request);
    
    @GetMapping
    public ApiResponse<PageResponse<Tenant>> listTenants(
        @RequestParam(required = false) TenantStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );
    
    @GetMapping("/{tenantId}")
    public ApiResponse<TenantDetailResponse> getTenantDetail(@PathVariable String tenantId);
    
    @PutMapping("/{tenantId}")
    public ApiResponse<Tenant> updateTenant(
        @PathVariable String tenantId,
        @RequestBody UpdateTenantRequest request
    );
    
    @PutMapping("/{tenantId}/status")
    public ApiResponse<Void> updateTenantStatus(
        @PathVariable String tenantId,
        @RequestBody UpdateTenantStatusRequest request
    );
    
    @DeleteMapping("/{tenantId}")
    public ApiResponse<Void> deleteTenant(@PathVariable String tenantId);
}
```

#### FileAdminController

```java
@RestController
@RequestMapping("/api/v1/admin/files")
public class FileAdminController {
    
    @GetMapping
    public ApiResponse<PageResponse<FileRecord>> listFiles(FileQuery query);
    
    @GetMapping("/{fileId}")
    public ApiResponse<FileRecord> getFileDetail(@PathVariable String fileId);
    
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String fileId);
    
    @PostMapping("/batch-delete")
    public ApiResponse<BatchDeleteResult> batchDeleteFiles(
        @RequestBody BatchDeleteRequest request
    );
    
    @GetMapping("/statistics")
    public ApiResponse<StorageStatistics> getStatistics();
    
    @GetMapping("/statistics/by-tenant")
    public ApiResponse<Map<String, TenantStorageStats>> getStatisticsByTenant();
}
```

### 7. 认证和授权

#### ApiKeyAuthFilter

```java
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final AdminProperties adminProperties;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        // 只对管理员 API 进行认证
        if (!request.getRequestURI().startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String apiKey = request.getHeader("X-Admin-Api-Key");
        
        if (apiKey == null || !isValidApiKey(apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            return;
        }
        
        // 设置认证上下文
        AdminContext.setAdminUser(getAdminUserFromApiKey(apiKey));
        AdminContext.setIpAddress(request.getRemoteAddr());
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            AdminContext.clear();
        }
    }
    
    private boolean isValidApiKey(String apiKey) {
        return adminProperties.getApiKeys().stream()
            .anyMatch(key -> key.getKey().equals(apiKey));
    }
}
```

#### AdminProperties 配置

```java
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {
    private List<ApiKeyConfig> apiKeys;
    
    public static class ApiKeyConfig {
        private String name;
        private String key;
        private List<String> permissions;
    }
}
```

## 数据模型

### 数据库表设计

#### tenants 表

```sql
CREATE TABLE tenants (
    tenant_id           VARCHAR(32) PRIMARY KEY,
    tenant_name         VARCHAR(128) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'active',
    max_storage_bytes   BIGINT NOT NULL DEFAULT 10737418240,
    max_file_count      INTEGER NOT NULL DEFAULT 10000,
    max_single_file_size BIGINT NOT NULL DEFAULT 104857600,
    allowed_file_types  TEXT[],
    contact_email       VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_created_at ON tenants(created_at);
```

#### tenant_usage 表

```sql
-- 注意：不使用物理外键约束，通过应用层逻辑保证与 tenants 表的数据一致性
CREATE TABLE tenant_usage (
    tenant_id           VARCHAR(32) PRIMARY KEY,
    used_storage_bytes  BIGINT NOT NULL DEFAULT 0,
    used_file_count     INTEGER NOT NULL DEFAULT 0,
    last_upload_at      TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_usage_updated_at ON tenant_usage(updated_at);
```

#### admin_audit_logs 表

```sql
CREATE TABLE admin_audit_logs (
    id              VARCHAR(36) PRIMARY KEY,
    admin_user_id   VARCHAR(64) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       VARCHAR(64),
    tenant_id       VARCHAR(32),
    details         JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_admin ON admin_audit_logs(admin_user_id);
CREATE INDEX idx_audit_action ON admin_audit_logs(action);
CREATE INDEX idx_audit_time ON admin_audit_logs(created_at);
CREATE INDEX idx_audit_tenant ON admin_audit_logs(tenant_id);
```

#### file_records 表扩展

现有的 file_records 表需要添加 tenant_id 字段（如果还没有）：

```sql
-- 如果 tenant_id 字段不存在，添加该字段
ALTER TABLE file_records ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_file_records_tenant ON file_records(tenant_id);
```

### 存储路径格式

文件存储路径遵循以下格式：

```
{bucket}/{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
```

示例：
- 公开文件：`platform-files-public/blog/2026/01/21/user123/images/abc123.jpg`
- 私有文件：`platform-files-private/im/2026/01/21/user456/files/def456.pdf`

路径生成逻辑：

```java
public class StoragePathGenerator {
    public String generatePath(
        String tenantId,
        String userId,
        String fileId,
        String originalFilename,
        String contentType
    ) {
        LocalDateTime now = LocalDateTime.now();
        String type = determineTypeFromContentType(contentType);
        String extension = getFileExtension(originalFilename);
        
        return String.format(
            "%s/%04d/%02d/%02d/%s/%s/%s.%s",
            tenantId,
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            userId,
            type,
            fileId,
            extension
        );
    }
    
    private String determineTypeFromContentType(String contentType) {
        if (contentType.startsWith("image/")) return "images";
        if (contentType.startsWith("video/")) return "videos";
        if (contentType.startsWith("audio/")) return "audios";
        return "files";
    }
}
```

### 配置模型

#### application.yml 配置示例

```yaml
storage:
  type: s3
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:9001}
    access-key: ${S3_ACCESS_KEY:fileservice}
    secret-key: ${S3_SECRET_KEY:fileservice123}
    public-bucket: platform-files-public
    private-bucket: platform-files-private
    region: us-east-1
    cdn-domain: ${CDN_DOMAIN:}  # 可选

  presigned:
    private-url-expire-hours: 1
    upload-url-expire-minutes: 15

tenant:
  defaults:
    max-storage-bytes: 10737418240     # 10GB
    max-file-count: 10000
    max-single-file-size: 104857600    # 100MB
  auto-create: true

admin:
  api-keys:
    - name: admin-console
      key: ${ADMIN_API_KEY}
      permissions: [READ, WRITE, DELETE]
    - name: monitoring
      key: ${MONITORING_API_KEY}
      permissions: [READ]
```

## 正确性属性

属性是系统在所有有效执行中应该保持为真的特征或行为——本质上是关于系统应该做什么的形式化陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。

以下属性通过基于属性的测试进行验证，确保系统在各种输入下的正确性。

### 属性 1：文件存储桶选择

*对于任何*文件上传操作，当访问级别为 PUBLIC 时，文件应该被存储到 Public_Bucket；当访问级别为 PRIVATE 时，文件应该被存储到 Private_Bucket。

**验证需求：1.2**

### 属性 2：公开文件 URL 生成

*对于任何*访问级别为 PUBLIC 的文件，获取文件 URL 时应该返回 Public_Bucket 的直接访问 URL，而不是预签名 URL。

**验证需求：2.1**

### 属性 3：私有文件权限验证

*对于任何*访问级别为 PRIVATE 的文件，当非所有者用户请求访问时，系统应该拒绝请求并抛出 AccessDeniedException。

**验证需求：2.2, 2.3**

### 属性 4：私有文件预签名 URL 生成

*对于任何*访问级别为 PRIVATE 的文件，当所有者用户请求访问时，系统应该返回带有过期时间的预签名 URL。

**验证需求：2.4**

### 属性 5：新租户默认配额初始化

*对于任何*新创建的租户，系统应该使用配置的默认值初始化其配额（max_storage_bytes、max_file_count、max_single_file_size）。

**验证需求：3.4**

### 属性 6：租户最后上传时间更新

*对于任何*成功的文件上传操作，系统应该更新对应租户的最后上传时间为当前时间。

**验证需求：3.5**

### 属性 7：租户配额有效性验证

*对于任何*租户配额更新操作，当新配额值无效时（如负数、零或超出系统限制），系统应该拒绝更新并抛出异常。

**验证需求：4.5**

### 属性 8：配额更新时间戳记录

*对于任何*成功的租户配额更新操作，系统应该更新租户的 updated_at 时间戳为当前时间。

**验证需求：4.6**

### 属性 9：非活跃租户上传拒绝

*对于任何*状态不是 ACTIVE 的租户，当用户尝试上传文件时，系统应该拒绝上传并抛出 TenantSuspendedException。

**验证需求：5.1**

### 属性 10：存储空间配额检查

*对于任何*活跃租户的文件上传操作，当当前使用量加上新文件大小超过最大存储空间时，系统应该拒绝上传并抛出 QuotaExceededException。

**验证需求：5.3**

### 属性 11：文件数量配额检查

*对于任何*活跃租户的文件上传操作，当当前文件数量已达到最大文件数量时，系统应该拒绝上传并抛出 QuotaExceededException。

**验证需求：5.5**

### 属性 12：单文件大小限制检查

*对于任何*活跃租户的文件上传操作，当文件大小超过单文件大小限制时，系统应该拒绝上传并抛出 FileTooLargeException。

**验证需求：5.7**

### 属性 13：租户软删除保留数据

*对于任何*租户删除操作，系统应该将租户状态标记为 DELETED 而不是物理删除记录，以保留历史数据用于审计。

**验证需求：6.7**

### 属性 14：文件查询租户过滤

*对于任何*指定租户 ID 的文件查询，返回的所有文件记录的 tenant_id 应该等于查询条件中的租户 ID。

**验证需求：7.2**

### 属性 15：文件查询用户过滤

*对于任何*指定用户 ID 的文件查询，返回的所有文件记录的 user_id 应该等于查询条件中的用户 ID。

**验证需求：7.3**

### 属性 16：文件查询内容类型过滤

*对于任何*指定内容类型的文件查询，返回的所有文件记录的 content_type 应该等于查询条件中的内容类型。

**验证需求：7.4**

### 属性 17：文件查询访问级别过滤

*对于任何*指定访问级别的文件查询，返回的所有文件记录的 access_level 应该等于查询条件中的访问级别。

**验证需求：7.5**

### 属性 18：文件查询时间范围过滤

*对于任何*指定时间范围的文件查询，返回的所有文件记录的创建时间应该在查询条件指定的时间范围内。

**验证需求：7.6**

### 属性 19：文件查询大小范围过滤

*对于任何*指定大小范围的文件查询，返回的所有文件记录的文件大小应该在查询条件指定的大小范围内。

**验证需求：7.7**

### 属性 20：文件查询结果排序

*对于任何*指定排序字段和排序方向的文件查询，返回的文件记录列表应该按照指定字段和方向正确排序。

**验证需求：7.8**

### 属性 21：文件删除存储清理

*对于任何*文件删除操作，系统应该从存储系统中删除对应的文件对象，删除后该文件对象不应该存在于存储系统中。

**验证需求：8.2**

### 属性 22：文件删除记录清理

*对于任何*文件删除操作，系统应该从数据库中删除对应的文件记录，删除后该文件记录不应该存在于数据库中。

**验证需求：8.3**

### 属性 23：文件删除统计更新

*对于任何*文件删除操作，系统应该减少对应租户的已使用存储空间和文件数量，减少的数值应该等于被删除文件的大小和数量。

**验证需求：8.4**

### 属性 24：批量删除完整性

*对于任何*批量删除操作，系统应该尝试删除列表中的每个文件，即使某些文件删除失败，也应该继续删除其他文件。

**验证需求：8.6, 8.7**

### 属性 25：存储统计访问级别分类

*对于任何*存储统计查询，返回的统计结果中公开文件数量加上私有文件数量应该等于总文件数量。

**验证需求：9.2**

### 属性 26：存储统计文件类型分布

*对于任何*存储统计查询，返回的各文件类型数量之和应该等于总文件数量。

**验证需求：9.3**

### 属性 27：统计响应时间戳

*对于任何*存储统计查询，返回的统计结果应该包含统计时间戳，且该时间戳应该接近当前时间。

**验证需求：9.5**

### 属性 28：审计日志完整记录

*对于任何*管理员操作（删除文件、创建租户、更新租户等），系统应该记录审计日志，包含操作者 ID、操作类型、目标类型、目标 ID、租户 ID、详细信息、IP 地址和时间戳。

**验证需求：10.1**

### 属性 29：存储路径格式正确性

*对于任何*文件上传操作，生成的存储路径应该符合格式 `{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}`，其中日期部分使用上传时的日期，type 根据 MIME 类型确定。

**验证需求：11.1**

### 属性 30：上传成功统计增加

*对于任何*成功的文件上传操作，系统应该增加对应租户的已使用存储空间（增加文件大小）、已使用文件数量（增加 1）并更新最后上传时间。

**验证需求：12.1**

### 属性 31：删除成功统计减少

*对于任何*成功的文件删除操作，系统应该减少对应租户的已使用存储空间（减少文件大小）和已使用文件数量（减少 1）。

**验证需求：12.4**

### 属性 32：统计更新原子性

*对于任何*并发的文件上传和删除操作，租户使用统计的更新应该是原子性的，最终统计结果应该与操作序列一致。

**验证需求：12.6**

### 属性 33：管理员 API 认证要求

*对于任何*管理员 API 请求，如果请求头中不包含有效的 X-Admin-Api-Key，系统应该拒绝请求并返回 401 未授权错误。

**验证需求：13.1**

### 属性 34：租户自动创建

*对于任何*未知的租户 ID，当启用租户自动创建功能时，系统应该自动创建该租户并使用默认配额，租户状态应该为 ACTIVE。

**验证需求：14.1**

### 属性 35：租户自动创建禁用

*对于任何*未知的租户 ID，当禁用租户自动创建功能时，系统应该抛出 TenantNotFoundException 异常。

**验证需求：14.4**

### 属性 36：异常消息清晰性

*对于任何*系统抛出的异常（TenantNotFoundException、TenantSuspendedException、QuotaExceededException、FileTooLargeException、AccessDeniedException），异常消息应该包含清晰的错误描述和相关上下文信息。

**验证需求：15.6**

## 错误处理

### 异常类型

系统定义以下自定义异常类型：

1. **TenantNotFoundException**: 租户不存在
   - 错误代码：TENANT_NOT_FOUND
   - HTTP 状态码：404

2. **TenantSuspendedException**: 租户已被停用
   - 错误代码：TENANT_SUSPENDED
   - HTTP 状态码：403

3. **QuotaExceededException**: 配额超限
   - 错误代码：QUOTA_EXCEEDED
   - HTTP 状态码：413 (Payload Too Large，语义更准确)
   - 包含超限类型：STORAGE_QUOTA、FILE_COUNT_QUOTA

4. **FileTooLargeException**: 文件过大
   - 错误代码：FILE_TOO_LARGE
   - HTTP 状态码：413
   - 包含文件大小和限制大小

5. **AccessDeniedException**: 访问被拒绝
   - 错误代码：ACCESS_DENIED
   - HTTP 状态码：403

6. **FileNotFoundException**: 文件不存在
   - 错误代码：FILE_NOT_FOUND
   - HTTP 状态码：404

### 错误响应格式

```json
{
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "Storage quota exceeded: 10737418240 bytes used, limit is 10737418240 bytes",
    "details": {
      "tenantId": "blog",
      "currentUsage": 10737418240,
      "limit": 10737418240,
      "quotaType": "STORAGE_QUOTA"
    },
    "timestamp": "2026-01-21T10:30:00Z"
  }
}
```

### 错误处理策略

1. **配额检查失败**：在上传前检查，立即返回错误，不执行上传操作
2. **租户状态异常**：在操作前检查，立即返回错误
3. **文件删除失败**：记录错误日志，对于批量删除继续处理其他文件
4. **存储服务异常**：捕获并转换为业务异常，提供清晰的错误信息
5. **并发冲突**：使用乐观锁或悲观锁处理，必要时重试

## 测试策略

### 单元测试

单元测试验证特定示例、边界情况和错误条件：

1. **配置测试**：验证双 Bucket 配置正确加载
2. **路径生成测试**：验证存储路径格式正确
3. **权限验证测试**：验证文件所有者和非所有者的访问权限
4. **配额计算测试**：验证配额检查的边界情况（刚好达到限制、超出 1 字节等）
5. **异常处理测试**：验证各种异常情况下的错误消息和状态码
6. **审计日志测试**：验证审计日志记录的完整性

### 基于属性的测试

基于属性的测试验证通用属性在所有输入下的正确性：

1. **存储桶选择属性**：生成随机的访问级别和文件，验证文件被存储到正确的桶
2. **URL 生成属性**：生成随机的文件和用户，验证 URL 类型正确（直接 URL vs 预签名 URL）
3. **配额检查属性**：生成随机的租户使用量和文件大小，验证配额检查逻辑
4. **过滤查询属性**：生成随机的文件集合和查询条件，验证过滤结果正确
5. **统计计算属性**：生成随机的文件集合，验证统计结果准确
6. **并发安全属性**：生成随机的并发操作序列，验证统计更新的原子性

### 测试配置

- 每个属性测试运行最少 100 次迭代
- 使用 jqwik 作为 Java 的属性测试库
- 每个属性测试使用注释标记对应的设计属性：
  ```java
  // Feature: file-service-optimization, Property 1: 文件存储桶选择
  @Property
  void fileStorageBucketSelection(@ForAll AccessLevel accessLevel, @ForAll FileData fileData) {
      // 测试实现
  }
  ```

### 集成测试

集成测试验证组件之间的交互：

1. **端到端上传流程**：从 API 请求到存储系统的完整流程
2. **文件删除流程**：验证文件、记录和统计的一致性删除
3. **租户管理流程**：验证租户创建、更新、停用的完整流程
4. **管理员 API 认证**：验证 API Key 认证和授权流程
5. **审计日志记录**：验证管理员操作触发审计日志记录

### 性能测试

1. **并发上传测试**：验证系统在高并发上传下的性能和稳定性
2. **大文件上传测试**：验证大文件上传的性能和内存使用
3. **批量删除测试**：验证批量删除的性能和事务处理
4. **统计查询测试**：验证大数据量下的统计查询性能
