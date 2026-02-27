# Implementation Tasks

## Overview

本文档定义了在 file-service 中实现文件 URL 缓存功能的具体实现任务。所有任务按照依赖关系排序，每个任务都有明确的验收标准。

**预计总工时**: 2.5 小时  
**优先级**: 高  
**目标完成时间**: 2 个工作日

## Tasks

- [x] 1. 添加 Redis 依赖
  - [x] 1.1 打开 `file-service/file-service/pom.xml`
  - [x] 1.2 在 `<dependencies>` 部分添加 Redis 依赖
  - [x] 1.3 运行 `mvn clean compile` 验证依赖下载成功
  - _Requirements: 基础设施配置_

- [x] 2. 创建 FileRedisKeys 常量类
  - [x] 2.1 创建包 `com.architectcgz.file.infrastructure.cache`
  - [x] 2.2 创建类 `FileRedisKeys.java`
  - [x] 2.3 实现 `fileUrl(String fileId)` 静态方法
  - [x] 2.4 添加完整的中文注释
  - _Requirements: 常量管理规范_

- [x] 3. 创建 CacheProperties 配置类
  - [x] 3.1 创建类 `com.architectcgz.file.infrastructure.config.CacheProperties`
  - [x] 3.2 使用 `@ConfigurationProperties` 注解
  - [x] 3.3 定义 `enabled` 和 `url.ttl` 属性
  - [x] 3.4 添加完整的中文注释
  - _Requirements: 配置管理_

- [x] 4. 配置 Redis 连接
  - [x] 4.1 打开 `file-service/file-service/src/main/resources/application.yml`
  - [x] 4.2 添加 Redis 配置
  - [x] 4.3 添加缓存配置
  - _Requirements: 基础设施配置_

- [x] 5. 修改 FileAccessService - 添加缓存读取
  - [x] 5.1 在 FileAccessService 中注入 RedisTemplate 和 CacheProperties
  - [x] 5.2 创建私有方法 `getCachedUrl(String fileId)`
  - [x] 5.3 在 `getFileUrl()` 方法开始处调用缓存读取
  - [x] 5.4 实现降级逻辑（Redis 异常时返回 null）
  - [x] 5.5 添加 DEBUG 日志
  - _Requirements: 缓存读取逻辑_

- [x] 6. 修改 FileAccessService - 添加缓存写入
  - [x] 6.1 创建私有方法 `cacheUrl(String fileId, String url)`
  - [x] 6.2 在查询数据库后调用缓存写入
  - [x] 6.3 使用配置的 TTL
  - [x] 6.4 实现异常处理（缓存失败不影响业务）
  - [x] 6.5 添加 DEBUG 日志
  - _Requirements: 缓存写入逻辑_

- [x] 7. 修改 FileManagementService - 添加缓存清除
  - [x] 7.1 在 FileManagementService 中注入 RedisTemplate 和 CacheProperties
  - [x] 7.2 创建私有方法 `clearCache(String fileId)`
  - [x] 7.3 在 `deleteFile()` 方法中调用缓存清除
  - [x] 7.4 实现异常处理（缓存清除失败不影响删除）
  - [x] 7.5 添加 INFO 日志
  - _Requirements: 缓存清除逻辑_

- [ ] 8. 编写单元测试
  - [x] 8.1 创建测试类 `FileAccessServiceCacheTest`
  - [x] 8.2 使用 Mockito Mock RedisTemplate
  - [x] 8.3 编写测试用例
  - _Requirements: 单元测试覆盖_

- [-] 9. 编写集成测试
  - [x] 9.1 创建测试类 `FileCacheIntegrationTest`
  - [ ] 9.2 使用 Testcontainers 启动 Redis
  - [ ] 9.3 编写集成测试用例
  - _Requirements: 集成测试覆盖_

- [ ] 10. 添加监控指标（可选）
  - [ ] 10.1 在 FileAccessService 中注入 MeterRegistry
  - [ ] 10.2 在缓存操作中记录指标
  - [ ] 10.3 添加缓存命中率计算
  - _Requirements: 监控指标_

- [ ] 11. 更新文档
  - [ ] 11.1 更新 `file-service/file-service/README.md`
  - [ ] 11.2 更新 `file-service/file-service/API.md`
  - [ ] 11.3 添加缓存配置说明
  - _Requirements: 文档更新_

---

## ⚠️ 重要提醒

**完成任务后必须标记：**
1. 完成任务的所有实现步骤后，必须在该任务的"验收标准"部分将所有复选框标记为 `[x]`
2. 在文档底部的"Task Checklist"部分将对应任务标记为 `[x]`
3. 只有当所有验收标准都满足时，才能标记任务为完成

**示例：**
```markdown
**验收标准**:
- [x] 已完成的标准
- [x] 已完成的标准
- [ ] 未完成的标准  ← 必须全部完成才能标记任务完成
```

---

## Task 1: 添加 Redis 依赖

**描述**: 在 file-service 的 pom.xml 中添加 Spring Data Redis 依赖

**优先级**: P0（必须最先完成）

**预计工时**: 5 分钟

**依赖**: 无

- [x] 1.1 打开 `file-service/file-service/pom.xml`
- [x] 1.2 在 `<dependencies>` 部分添加 Redis 依赖
- [x] 1.3 运行 `mvn clean compile` 验证依赖下载成功

**验收标准**:
- pom.xml 包含 `spring-boot-starter-data-redis` 依赖
- Maven 构建成功
- 依赖版本与 Spring Boot 版本兼容

**代码示例**:
```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## Task 2: 创建 FileRedisKeys 常量类

**描述**: 创建 Redis Key 管理类，统一管理文件相关的缓存 Key

**优先级**: P0

**预计工时**: 10 分钟

**依赖**: Task 1

- [x] 2.1 创建包 `com.architectcgz.file.infrastructure.cache`
- [x] 2.2 创建类 `FileRedisKeys.java`
- [x] 2.3 实现 `fileUrl(String fileId)` 静态方法
- [x] 2.4 添加完整的中文注释

**验收标准**:
- 类位于正确的包路径
- 提供 `fileUrl(String fileId)` 静态方法
- Key 格式为 `file:{fileId}:url`
- 包含完整的类和方法注释（中文）
- 符合常量管理规范

**代码模板**:
```java
package com.architectcgz.file.infrastructure.cache;

/**
 * 文件服务 Redis Key 管理类
 * 
 * 统一管理所有文件相关的缓存 Key，避免硬编码
 */
public class FileRedisKeys {
    
    private static final String PREFIX = "file";
    
    /**
     * 文件 URL 缓存 Key
     * 格式: file:{fileId}:url
     * 
     * @param fileId 文件ID
     * @return Redis Key
     */
    public static String fileUrl(String fileId) {
        return PREFIX + ":" + fileId + ":url";
    }
}
```

---

## Task 3: 创建 CacheProperties 配置类

**描述**: 创建缓存配置属性类，支持通过配置文件调整缓存参数

**优先级**: P0

**预计工时**: 10 分钟

**依赖**: Task 1

- [x] 3.1 创建类 `com.architectcgz.file.infrastructure.config.CacheProperties`
- [x] 3.2 使用 `@ConfigurationProperties` 注解
- [x] 3.3 定义 `enabled` 和 `url.ttl` 属性
- [x] 3.4 添加完整的中文注释

**验收标准**:
- 类使用 `@ConfigurationProperties(prefix = "file-service.cache")`
- 包含 `enabled` 属性（默认 true）
- 包含 `url.ttl` 属性（默认 3600）
- 使用 Lombok `@Data` 注解
- 包含完整的注释

**代码模板**:
```java
package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-service.cache")
public class CacheProperties {
    
    /**
     * 是否启用缓存
     */
    private boolean enabled = true;
    
    /**
     * URL 缓存配置
     */
    private UrlCache url = new UrlCache();
    
    @Data
    public static class UrlCache {
        /**
         * 缓存过期时间（秒）
         */
        private long ttl = 3600; // 1小时
    }
}
```

---

## Task 4: 配置 Redis 连接

**描述**: 在 application.yml 中添加 Redis 连接配置

**优先级**: P0

**预计工时**: 5 分钟

**依赖**: Task 1

- [x] 4.1 打开 `file-service/file-service/src/main/resources/application.yml`
- [x] 4.2 添加 Redis 配置
- [x] 4.3 添加缓存配置

**验收标准**:
- 包含 Redis 连接配置
- 支持环境变量覆盖
- 包含连接池配置
- 包含缓存开关和 TTL 配置

**配置模板**:
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis123456}
      database: ${REDIS_DATABASE:0}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

file-service:
  cache:
    enabled: ${CACHE_ENABLED:true}
    url:
      ttl: ${CACHE_URL_TTL:3600}
```

---

## Task 5: 修改 FileAccessService - 添加缓存读取

**描述**: 在 FileAccessService.getFileUrl() 方法中添加缓存读取逻辑

**优先级**: P1

**预计工时**: 20 分钟

**依赖**: Task 2, Task 3, Task 4

- [x] 5.1 在 FileAccessService 中注入 RedisTemplate 和 CacheProperties
- [x] 5.2 创建私有方法 `getCachedUrl(String fileId)`
- [x] 5.3 在 `getFileUrl()` 方法开始处调用缓存读取
- [x] 5.4 实现降级逻辑（Redis 异常时返回 null）
- [x] 5.5 添加 DEBUG 日志

**验收标准**:
- 注入 `RedisTemplate<String, String>` 和 `CacheProperties`
- 缓存命中时直接返回 URL
- 缓存未命中时继续查询数据库
- Redis 异常时降级到数据库查询
- 记录缓存命中/未命中日志（DEBUG 级别）
- 包含完整的中文注释

**代码模板**:
```java
@Autowired
private RedisTemplate<String, String> redisTemplate;

@Autowired
private CacheProperties cacheProperties;

/**
 * 从缓存获取文件 URL
 * 
 * @param fileId 文件ID
 * @return 缓存的 URL，如果不存在返回 null
 */
private String getCachedUrl(String fileId) {
    if (!cacheProperties.isEnabled()) {
        return null;
    }
    
    try {
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedUrl != null) {
            log.debug("Cache hit: fileId={}", fileId);
            return cachedUrl;
        }
        
        log.debug("Cache miss: fileId={}", fileId);
        return null;
    } catch (Exception e) {
        log.warn("Failed to get cached URL, fallback to database: fileId={}", fileId, e);
        return null;
    }
}

public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
    // 1. 尝试从缓存获取
    String cachedUrl = getCachedUrl(fileId);
    if (cachedUrl != null) {
        // 构建响应（需要判断是公开还是私有文件）
        // ...
    }
    
    // 2. 缓存未命中，查询数据库
    FileRecord file = fileRecordRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("文件不存在: " + fileId));
    
    // ... 原有逻辑
}
```

---

## Task 6: 修改 FileAccessService - 添加缓存写入

**描述**: 在 FileAccessService.getFileUrl() 方法中添加缓存写入逻辑

**优先级**: P1

**预计工时**: 15 分钟

**依赖**: Task 5

- [x] 6.1 创建私有方法 `cacheUrl(String fileId, String url)`
- [x] 6.2 在查询数据库后调用缓存写入
- [x] 6.3 使用配置的 TTL
- [x] 6.4 实现异常处理（缓存失败不影响业务）
- [x] 6.5 添加 DEBUG 日志

**验收标准**:
- 数据库查询成功后写入缓存
- 使用 `FileRedisKeys.fileUrl()` 生成 Key
- 使用 `cacheProperties.getUrl().getTtl()` 设置 TTL
- 缓存写入失败不影响业务流程
- 记录缓存写入日志（DEBUG 级别）
- 包含完整的中文注释

**代码模板**:
```java
/**
 * 将文件 URL 写入缓存
 * 
 * @param fileId 文件ID
 * @param url 文件访问URL
 */
private void cacheUrl(String fileId, String url) {
    if (!cacheProperties.isEnabled()) {
        return;
    }
    
    try {
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        long ttl = cacheProperties.getUrl().getTtl();
        
        redisTemplate.opsForValue().set(cacheKey, url, ttl, TimeUnit.SECONDS);
        log.debug("Cached URL: fileId={}, ttl={}s", fileId, ttl);
    } catch (Exception e) {
        log.warn("Failed to cache URL: fileId={}", fileId, e);
        // 缓存失败不影响业务流程
    }
}
```

---

## Task 7: 修改 FileManagementService - 添加缓存清除

**描述**: 在文件删除时清除对应的 URL 缓存

**优先级**: P1

**预计工时**: 15 分钟

**依赖**: Task 2, Task 3

- [x] 7.1 在 FileManagementService 中注入 RedisTemplate 和 CacheProperties
- [x] 7.2 创建私有方法 `clearCache(String fileId)`
- [x] 7.3 在 `deleteFile()` 方法中调用缓存清除
- [x] 7.4 实现异常处理（缓存清除失败不影响删除）
- [x] 7.5 添加 INFO 日志

**验收标准**:
- 注入 `RedisTemplate<String, String>` 和 `CacheProperties`
- 文件删除成功后清除缓存
- 使用 `FileRedisKeys.fileUrl()` 生成 Key
- 缓存清除失败不影响文件删除
- 记录缓存清除日志（INFO 级别）
- 包含完整的中文注释

**代码模板**:
```java
@Autowired
private RedisTemplate<String, String> redisTemplate;

@Autowired
private CacheProperties cacheProperties;

/**
 * 清除文件 URL 缓存
 * 
 * @param fileId 文件ID
 */
private void clearCache(String fileId) {
    if (!cacheProperties.isEnabled()) {
        return;
    }
    
    try {
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        Boolean deleted = redisTemplate.delete(cacheKey);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cache cleared: fileId={}", fileId);
        } else {
            log.debug("Cache not found: fileId={}", fileId);
        }
    } catch (Exception e) {
        log.warn("Failed to clear cache: fileId={}", fileId, e);
        // 缓存清除失败不影响业务流程
    }
}

public void deleteFile(String appId, String fileId, String requestUserId) {
    // ... 原有删除逻辑
    
    // 清除缓存
    clearCache(fileId);
}
```

---

## Task 8: 编写单元测试

**描述**: 为缓存功能编写单元测试

**优先级**: P2

**预计工时**: 30 分钟

**依赖**: Task 5, Task 6, Task 7

- [ ] 8.1 创建测试类 `FileAccessServiceCacheTest`
- [ ] 8.2 使用 Mockito Mock RedisTemplate
- [ ] 8.3 编写测试用例

**验收标准**:
- 测试缓存命中场景
- 测试缓存未命中场景
- 测试缓存写入
- 测试缓存清除
- 测试 Redis 异常时的降级
- 测试缓存开关配置
- 测试覆盖率 > 80%

**测试用例模板**:
```java
@ExtendWith(MockitoExtension.class)
class FileAccessServiceCacheTest {
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @Mock
    private FileRecordRepository fileRecordRepository;
    
    @InjectMocks
    private FileAccessService fileAccessService;
    
    @Test
    void testCacheHit() {
        // Given
        String fileId = "01JGXXX";
        String cachedUrl = "https://example.com/file.jpg";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FileRedisKeys.fileUrl(fileId))).thenReturn(cachedUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user1");
        
        // Then
        assertEquals(cachedUrl, response.getUrl());
        verify(fileRecordRepository, never()).findById(any());
    }
    
    @Test
    void testCacheMiss() {
        // Given
        String fileId = "01JGXXX";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FileRedisKeys.fileUrl(fileId))).thenReturn(null);
        
        FileRecord file = new FileRecord();
        file.setId(fileId);
        file.setStoragePath("path/to/file.jpg");
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(file));
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user1");
        
        // Then
        assertNotNull(response.getUrl());
        verify(fileRecordRepository).findById(fileId);
        verify(valueOperations).set(eq(FileRedisKeys.fileUrl(fileId)), anyString(), anyLong(), any());
    }
}
```

---

## Task 9: 编写集成测试

**描述**: 编写集成测试验证缓存功能

**优先级**: P2

**预计工时**: 30 分钟

**依赖**: Task 8

- [ ] 9.1 创建测试类 `FileCacheIntegrationTest`
- [ ] 9.2 使用 Testcontainers 启动 Redis
- [ ] 9.3 编写集成测试用例

**验收标准**:
- 使用真实的 Redis（Testcontainers）
- 测试完整的缓存读写流程
- 测试文件删除后缓存清除
- 测试 TTL 过期
- 测试并发访问
- 所有测试通过

**测试用例模板**:
```java
@SpringBootTest
@Testcontainers
class FileCacheIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private FileAccessService fileAccessService;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Test
    void testCacheFlow() {
        // 第一次查询（缓存未命中）
        FileUrlResponse response1 = fileAccessService.getFileUrl("blog", "01JGXXX", "user1");
        
        // 验证缓存已写入
        String cachedUrl = redisTemplate.opsForValue().get(FileRedisKeys.fileUrl("01JGXXX"));
        assertNotNull(cachedUrl);
        
        // 第二次查询（缓存命中）
        FileUrlResponse response2 = fileAccessService.getFileUrl("blog", "01JGXXX", "user1");
        
        // 验证返回相同的 URL
        assertEquals(response1.getUrl(), response2.getUrl());
    }
}
```

---

## Task 10: 添加监控指标

**描述**: 使用 Micrometer 添加缓存监控指标

**优先级**: P3（可选）

**预计工时**: 15 分钟

**依赖**: Task 5, Task 6, Task 7

- [ ] 10.1 在 FileAccessService 中注入 MeterRegistry
- [ ] 10.2 在缓存操作中记录指标
- [ ] 10.3 添加缓存命中率计算

**验收标准**:
- 记录缓存命中次数（Counter）
- 记录缓存未命中次数（Counter）
- 记录缓存写入次数（Counter）
- 记录缓存删除次数（Counter）
- 记录缓存操作耗时（Timer）
- 可通过 /actuator/metrics 查看指标

**代码模板**:
```java
@Autowired
private MeterRegistry meterRegistry;

private String getCachedUrl(String fileId) {
    try {
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedUrl != null) {
            meterRegistry.counter("file.cache.hit").increment();
            log.debug("Cache hit: fileId={}", fileId);
            return cachedUrl;
        }
        
        meterRegistry.counter("file.cache.miss").increment();
        log.debug("Cache miss: fileId={}", fileId);
        return null;
    } catch (Exception e) {
        meterRegistry.counter("file.cache.error").increment();
        log.warn("Failed to get cached URL: fileId={}", fileId, e);
        return null;
    }
}
```

---

## Task 11: 更新文档

**描述**: 更新项目文档，说明缓存功能

**优先级**: P3

**预计工时**: 10 分钟

**依赖**: 所有实现任务完成

- [ ] 11.1 更新 `file-service/file-service/README.md`
- [ ] 11.2 更新 `file-service/file-service/API.md`
- [ ] 11.3 添加缓存配置说明

**验收标准**:
- README 包含缓存功能说明
- README 包含 Redis 配置说明
- API 文档说明缓存对性能的影响
- 包含缓存开关配置示例

---

## Task Checklist

### Phase 1: 基础设施（30 分钟）
- [x] Task 1: 添加 Redis 依赖
- [x] Task 2: 创建 FileRedisKeys 常量类
- [x] Task 3: 创建 CacheProperties 配置类
- [ ] Task 4: 配置 Redis 连接

### Phase 2: 核心功能（50 分钟）
- [x] Task 5: 修改 FileAccessService - 添加缓存读取
- [x] Task 6: 修改 FileAccessService - 添加缓存写入
- [-] Task 7: 修改 FileManagementService - 添加缓存清除

### Phase 3: 测试（60 分钟）
- [ ] Task 8: 编写单元测试
- [ ] Task 9: 编写集成测试

### Phase 4: 监控和文档（25 分钟）
- [ ] Task 10: 添加监控指标（可选）
- [ ] Task 11: 更新文档

## Testing Checklist

### 功能测试
- [ ] 缓存命中场景正常工作
- [ ] 缓存未命中场景正常工作
- [ ] 缓存写入成功
- [ ] 文件删除后缓存被清除
- [ ] Redis 不可用时降级到数据库查询
- [ ] 缓存开关配置生效

### 性能测试
- [ ] 缓存命中时响应时间 < 10ms
- [ ] 缓存未命中时响应时间 < 50ms
- [ ] 缓存命中率 > 80%

### 集成测试
- [ ] 与 blog-upload 服务集成正常
- [ ] 与 blog-post 服务集成正常
- [ ] TTL 过期后重新查询数据库

## Deployment Checklist

### 开发环境
- [ ] Redis 服务启动（docker-compose）
- [ ] 配置文件正确
- [ ] 应用启动成功
- [ ] 缓存功能正常

### 测试环境
- [ ] Redis 服务配置
- [ ] 环境变量配置
- [ ] 监控指标可见
- [ ] 告警规则配置

### 生产环境
- [ ] Redis 高可用配置
- [ ] 连接池参数调优
- [ ] 监控面板配置
- [ ] 告警规则生效
- [ ] 回滚方案就绪

## Related Documents

- [需求文档](./requirements.md)
- [设计文档](./design.md)
- [缓存架构决策文档](../file-service-fileid-migration/caching-architecture.md)

---

**文档版本**: 1.0  
**创建日期**: 2026-02-09  
**最后更新**: 2026-02-09  
**作者**: 开发团队
