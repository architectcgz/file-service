# file-service 代码 Review（cache-gap 第 1 轮）：访问级别变更时清除文件 URL 缓存

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | cache-gap |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit `3b5a45d`，1 个文件，+31/-2 行 |
| 变更概述 | 在 `FileAccessService.updateAccessLevel()` 中，数据库更新成功后清除 `file:{fileId}:url` 缓存，防止 PRIVATE 文件通过旧缓存公开访问 |
| 审查基准 | 无架构文档，以项目现有代码风格和全局 CLAUDE.md 规范为基准 |
| 审查日期 | 2026-02-28 |

## 问题清单

### 🔴 高优先级

#### [H1] 缓存清除逻辑重复，应抽取为共享组件

- **文件**：
  - `file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 275-293 行（`evictUrlCache`）
  - `file-service/src/main/java/com/architectcgz/file/application/service/FileManagementService.java` 第 258-276 行（`clearCache`）
- **问题描述**：`FileAccessService.evictUrlCache()` 与 `FileManagementService.clearCache()` 逻辑完全相同——判断缓存开关、构建 key、调用 `redisTemplate.delete()`、异常吞掉并 warn。两处独立维护同一段缓存清除逻辑，未来修改（如增加多级缓存、增加 metrics 埋点）极易遗漏其中一处。
- **影响范围/风险**：维护成本翻倍，缓存策略变更时容易出现不一致行为。
- **修正建议**：将缓存清除逻辑抽取到 Infrastructure 层的统一缓存管理组件中（如 `FileUrlCacheManager`），两个 Service 都注入该组件调用。示例：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUrlCacheManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * 清除文件 URL 缓存
     *
     * @param fileId 文件ID
     */
    public void evict(String fileId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Evicted URL cache: fileId={}", fileId);
            } else {
                log.debug("No URL cache to evict: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict URL cache: fileId={}", fileId, e);
        }
    }
}
```

同时 `getCachedUrl()` 和 `cacheUrl()` 也应迁入该组件，使缓存读写逻辑完全收敛。

### 🟡 中优先级

#### [M1] `updateAccessLevel()` 缺少 `@Transactional` 注解，缓存清除时机存在风险

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 242 行
- **问题描述**：`updateAccessLevel()` 方法没有 `@Transactional` 注解。当前实现中 `fileRecordRepository.updateAccessLevel()` 会在 auto-commit 模式下立即提交，随后执行 `evictUrlCache()`。虽然当前场景只有一次数据库写操作，auto-commit 不会导致数据不一致，但对比同项目 `FileManagementService.deleteFile()` 使用了 `@Transactional`，风格不统一。更重要的是，如果未来 `updateAccessLevel()` 增加额外的数据库操作（如写审计日志），缺少事务保护会导致部分写入问题。
- **影响范围/风险**：当前无实际 bug，但与项目风格不一致，且扩展性差。
- **修正建议**：为 `updateAccessLevel()` 添加 `@Transactional` 注解，并确保 `evictUrlCache()` 在事务提交后执行（可使用 `TransactionSynchronizationManager.registerSynchronization()` 或 `@TransactionalEventListener`），避免事务回滚后缓存已被清除的窗口期问题。

#### [M2] 缺少 `updateAccessLevel` 缓存清除的单元测试

- **文件**：`file-service/src/test/java/com/architectcgz/file/application/service/FileAccessServiceCacheTest.java`
- **问题描述**：现有测试类 `FileAccessServiceCacheTest` 覆盖了缓存读写和降级场景，但没有任何测试覆盖 `updateAccessLevel()` 触发缓存清除的行为。关键场景缺失：
  1. PUBLIC -> PRIVATE 时缓存被清除
  2. PRIVATE -> PUBLIC 时缓存被清除（虽然此时缓存中大概率无数据，但逻辑路径应覆盖）
  3. 缓存清除失败不阻断业务
  4. 缓存禁用时不调用 Redis
- **影响范围/风险**：本次变更的核心逻辑没有测试保护，后续重构可能无意中破坏缓存清除行为而不被发现。
- **修正建议**：在 `FileAccessServiceCacheTest` 中补充以下测试用例：

```java
@Test
@DisplayName("更新访问级别 - PUBLIC转PRIVATE时清除URL缓存")
void testUpdateAccessLevel_PublicToPrivate_EvictsCache() {
    // Given
    when(cacheProperties.isEnabled()).thenReturn(true);
    when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
    when(fileRecordRepository.updateAccessLevel("file-001", AccessLevel.PRIVATE)).thenReturn(true);
    when(redisTemplate.delete(FileRedisKeys.fileUrl("file-001"))).thenReturn(true);

    // When
    fileAccessService.updateAccessLevel("blog", "file-001", "user-123", AccessLevel.PRIVATE);

    // Then
    verify(redisTemplate).delete(FileRedisKeys.fileUrl("file-001"));
}

@Test
@DisplayName("更新访问级别 - 缓存清除失败不阻断业务")
void testUpdateAccessLevel_CacheEvictFails_DoesNotThrow() {
    // Given
    when(cacheProperties.isEnabled()).thenReturn(true);
    when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
    when(fileRecordRepository.updateAccessLevel("file-001", AccessLevel.PRIVATE)).thenReturn(true);
    when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

    // When & Then
    assertDoesNotThrow(() ->
        fileAccessService.updateAccessLevel("blog", "file-001", "user-123", AccessLevel.PRIVATE)
    );
}
```

### 🟢 低优先级

#### [L1] `evictUrlCache` 日志消息硬编码了变更原因，复用性差

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 284 行
- **问题描述**：日志 `"Evicted URL cache after access level change: fileId={}"` 将清除原因硬编码为 "access level change"。如果后续其他场景（如文件替换、存储路径变更）也需要清除缓存并复用此方法，日志描述将不准确。
- **影响范围/风险**：日志误导，排查问题时可能产生混淆。
- **修正建议**：如果按 [H1] 抽取为 `FileUrlCacheManager`，日志只记录"清除了什么"，不记录"为什么清除"。调用方在自己的 info 日志中记录原因即可。当前 `updateAccessLevel()` 第 265 行的 info 日志已经记录了变更上下文，`evictUrlCache` 内部用 debug 级别记录删除结果即可。

#### [L2] `updateAccessLevel()` 未校验 newLevel 与当前级别是否相同

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 242-267 行
- **问题描述**：当 `newLevel` 与文件当前的 `accessLevel` 相同时，仍然执行数据库更新和缓存清除，产生无意义的写操作和日志。
- **影响范围/风险**：无功能 bug，但浪费数据库写和 Redis 操作，且 info 日志中 `oldLevel` 和 `newLevel` 相同会造成困惑。
- **修正建议**：在数据库更新前增加短路判断：

```java
if (file.getAccessLevel() == newLevel) {
    log.debug("Access level unchanged, skip update: fileId={}, level={}", fileId, newLevel);
    return;
}
```

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 1 |
| 🟡 中 | 2 |
| 🟢 低 | 2 |
| 合计 | 5 |

## 总体评价

本次变更方向正确，精准解决了访问级别变更后缓存未清除导致 PRIVATE 文件仍可通过旧公开 URL 访问的安全漏洞。缓存 key 构建使用了统一的 `FileRedisKeys.fileUrl()`，与 `getFileUrl()` 中一致；异常处理采用 catch-and-warn 模式，不阻断业务流程，符合项目既有风格。

主要改进方向：

1. **消除重复**（[H1]）：`evictUrlCache` 与 `FileManagementService.clearCache` 完全重复，应抽取为共享的 `FileUrlCacheManager` 组件，将缓存读写清除逻辑统一收敛到 Infrastructure 层。
2. **事务安全**（[M1]）：补充 `@Transactional` 注解并确保缓存清除在事务提交后执行，为后续扩展留出安全空间。
3. **测试覆盖**（[M2]）：本次变更的核心逻辑（缓存清除）缺少单元测试，需补充。
