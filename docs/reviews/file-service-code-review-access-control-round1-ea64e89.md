# file-service 代码 Review（access-control 第 1 轮）：补全访问控制检查链，统一 canAccessFile 校验

## Review 信息

| 字段 | 说明 |
|------|------|
| 变更主题 | access-control |
| 轮次 | 第 1 轮（首次审查） |
| 审查范围 | commit ea64e89，1 个文件，+45/-35 行 |
| 变更概述 | canAccessFile() 方法签名新增 appId 参数，补全租户隔离 -> 文件状态 -> 访问级别的完整检查链；getFileUrl() 和 getFileDetail() 移除分散的重复检查，统一由 canAccessFile() 校验 |
| 审查基准 | docs/architecture/layered-architecture.md, docs/architecture/core-workflows.md |
| 审查日期 | 2026-02-28 |

## 问题清单

### 🔴 高优先级

#### [H1] getFileUrl() 缓存命中路径完全绕过访问控制

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 60-69 行
- **问题描述**：`getFileUrl()` 在第 60 行尝试从 Redis 缓存获取 URL，如果缓存命中则直接在第 64-68 行返回结果，完全跳过了第 76 行的 `canAccessFile()` 检查。这意味着：
  - 跨租户请求（不同 appId）可以通过缓存获取到其他租户的公开文件 URL
  - 已删除文件如果缓存未过期，仍然可以返回有效 URL
  - 文件从 PUBLIC 改为 PRIVATE 后，缓存 TTL 内任何人仍可获取 URL
- **影响范围/风险**：租户隔离失效 + 访问控制绕过，属于安全漏洞。虽然缓存中只存公开文件 URL，但缓存 key 仅基于 fileId（`FileRedisKeys.fileUrl(fileId)`），不包含 appId，导致跨租户可命中。
- **修正建议**：缓存命中后仍需执行访问控制检查，或者将 appId 纳入缓存 key。推荐方案：

```java
// 方案 A：缓存命中后仍做权限校验（推荐，最安全）
String cachedUrl = getCachedUrl(fileId);
if (cachedUrl != null) {
    // 缓存命中也需要校验权限（防止跨租户访问和已删除文件）
    FileRecord file = fileRecordRepository.findById(fileId)
            .orElseThrow(() -> FileNotFoundException.notFound(fileId));
    if (!canAccessFile(file, requestUserId, appId)) {
        // 清除无效缓存
        evictUrlCache(fileId);
        // 抛出对应异常（复用已有逻辑）
        throwAccessException(file, fileId, appId);
    }
    return FileUrlResponse.builder()
            .url(cachedUrl).permanent(true).expiresAt(null).build();
}

// 方案 B：缓存 key 加入 appId（性能更优但不防已删除文件）
// FileRedisKeys.fileUrl(appId, fileId) -> "file:{appId}:{fileId}:url"
```

#### [H2] canAccessFile() 与调用处的异常映射逻辑重复，且存在信息泄漏风险

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 76-85 行、第 181-189 行
- **问题描述**：`canAccessFile()` 返回 `false` 后，调用处需要再次检查 `file.isDeleted()` 和 `file.belongsToApp(appId)` 来决定抛哪种异常。这导致两个问题：
  1. 异常映射逻辑在 `getFileUrl()` 和 `getFileDetail()` 中完全重复（第 77-85 行 vs 第 182-189 行）
  2. 对于跨租户请求，当前返回 `AccessDeniedException("文件不属于该应用")` 暴露了文件存在性——攻击者可以通过 403 vs 404 的区别来枚举其他租户的文件 ID
- **影响范围/风险**：信息泄漏 + 代码重复。跨租户场景下应统一返回 404 而非 403，避免暴露文件存在性。
- **修正建议**：将异常抛出逻辑封装到 `canAccessFile()` 内部（改为 void 方法直接抛异常），或提取为独立的 `checkAccess()` 方法。跨租户访问应返回 `FileNotFoundException`：

```java
/**
 * 校验文件访问权限，校验失败直接抛出对应异常
 */
private void checkFileAccess(FileRecord file, String fileId, String requestUserId, String appId) {
    // 租户隔离：跨租户统一返回 404，不暴露文件存在性
    if (!file.belongsToApp(appId)) {
        log.warn("跨租户访问被拒绝: fileId={}, fileAppId={}, requestAppId={}",
                file.getId(), file.getAppId(), appId);
        throw FileNotFoundException.notFound(fileId);
    }
    // 已删除文件返回 404
    if (file.isDeleted()) {
        throw FileNotFoundException.deleted(fileId);
    }
    // 访问级别检查
    if (file.getAccessLevel() == AccessLevel.PRIVATE) {
        if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
            throw new AccessDeniedException("无权访问该文件: " + fileId);
        }
    }
}
```

#### [H3] updateAccessLevel() 未使用统一的访问控制检查，且缺少已删除文件校验

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 252-274 行
- **问题描述**：本次变更的目标是"统一由 canAccessFile() 校验"，但 `updateAccessLevel()` 方法仍然使用分散的手动检查（第 257-258 行检查 `belongsToApp`，第 262 行检查 `userId`），且完全缺少已删除文件的状态校验。这意味着可以对已删除的文件修改访问级别。
- **影响范围/风险**：
  - 与本次变更的设计意图不一致——"统一校验"未覆盖所有方法
  - 已删除文件可被修改访问级别，虽然不会直接造成安全问题，但属于业务逻辑缺陷
- **修正建议**：`updateAccessLevel()` 也应使用统一的访问控制检查，并额外校验文件状态：

```java
public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
    FileRecord file = fileRecordRepository.findById(fileId)
            .orElseThrow(() -> FileNotFoundException.notFound(fileId));

    // 统一访问控制检查（租户隔离 + 文件状态）
    checkFileAccess(file, fileId, requestUserId, appId);

    // 只有文件所有者可以修改访问级别
    if (!file.getUserId().equals(requestUserId)) {
        throw new AccessDeniedException("无权修改该文件的访问级别: " + fileId);
    }

    boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
    // ...
}
```

### 🟡 中优先级

#### [M1] canAccessFile() 可见性为 public，但应为 private 或 package-private

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 216 行
- **问题描述**：`canAccessFile()` 被声明为 `public`，但从代码搜索结果看，它仅在 `FileAccessService` 内部被 `getFileUrl()` 和 `getFileDetail()` 调用，没有外部调用者。作为内部权限校验方法，暴露为 public API 会增加误用风险，且方法签名变更时影响面更大。
- **影响范围/风险**：API 表面积过大，后续维护时可能被外部错误依赖。
- **修正建议**：将可见性改为 `private`。如果未来确实需要外部调用，再按需开放。

#### [M2] updateAccessLevel() 修改访问级别后未清除 URL 缓存

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 252-274 行
- **问题描述**：`docs/todo.md` 第 255-259 行已经明确记录了这个已知问题：文件从 PUBLIC 改为 PRIVATE 时，`updateAccessLevel()` 没有清除 Redis 中缓存的公开 URL。在缓存 TTL（默认 3600s）内，旧的公开 URL 仍会被返回，绕过访问控制。虽然这不是本次变更引入的问题，但本次变更的目标是"补全访问控制"，应一并修复。
- **影响范围/风险**：访问级别变更后存在最长 1 小时的缓存不一致窗口，期间访问控制失效。
- **修正建议**：在 `updateAccessLevel()` 成功后清除缓存：

```java
boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
if (!updated) {
    throw new BusinessException("更新文件访问级别失败: " + fileId);
}

// 清除 URL 缓存，确保访问级别变更立即生效
evictUrlCache(fileId);

log.info("File access level updated: fileId={}, oldLevel={}, newLevel={}, userId={}",
        fileId, file.getAccessLevel(), newLevel, requestUserId);
```

需要新增 `evictUrlCache()` 方法：

```java
private void evictUrlCache(String fileId) {
    if (!cacheProperties.isEnabled()) {
        return;
    }
    try {
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        redisTemplate.delete(cacheKey);
        log.debug("Evicted URL cache: fileId={}", fileId);
    } catch (Exception e) {
        log.warn("Failed to evict URL cache: fileId={}", fileId, e);
    }
}
```

#### [M3] updateAccessLevel() 中 file.getUserId() 可能为 null 导致 NPE

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 262 行
- **问题描述**：第 262 行 `file.getUserId().equals(requestUserId)` 未做 null 检查。而 `canAccessFile()` 第 237 行对同样的场景做了 `file.getUserId() != null` 的防御。如果 `FileRecord.userId` 为 null（例如系统自动生成的文件），此处会抛出 NPE。
- **影响范围/风险**：NPE 导致 500 错误，而非预期的 403。
- **修正建议**：

```java
if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
    throw new AccessDeniedException("无权修改该文件的访问级别: " + fileId);
}
```

#### [M4] 错误消息字符串重复硬编码

- **文件**：`file-service/src/main/java/com/architectcgz/file/application/service/FileAccessService.java` 第 82、84、186、188、258、263 行
- **问题描述**：以下错误消息在多处重复出现：
  - `"文件不属于该应用"` — 第 82 行、第 186 行、第 258 行（3 处）
  - `"无权访问该文件: "` — 第 84 行、第 188 行（2 处）
  - `"无权修改该文件的访问级别: "` — 第 263 行

  违反全局 CLAUDE.md 中"错误消息提取为常量类"的规范。
- **影响范围/风险**：消息不一致风险，修改时容易遗漏。
- **修正建议**：提取为常量类：

```java
public final class FileErrorMessages {
    public static final String FILE_NOT_BELONG_TO_APP = "文件不属于该应用";
    public static final String ACCESS_DENIED_PREFIX = "无权访问该文件: ";
    public static final String MODIFY_ACCESS_DENIED_PREFIX = "无权修改该文件的访问级别: ";

    private FileErrorMessages() {}
}
```

### 🟢 低优先级

#### [L1] 测试未覆盖跨租户访问场景

- **文件**：`file-service/src/test/java/com/architectcgz/file/application/service/FileAccessServiceTest.java`
- **问题描述**：现有测试用例全部使用 `appId="blog"` 与测试数据中的 `appId="blog"` 匹配。缺少以下关键场景的测试：
  - 跨租户访问（使用不同 appId 请求其他租户的文件）
  - `canAccessFile()` 新增 appId 参数后的直接单元测试
  - `updateAccessLevel()` 对已删除文件的行为测试
- **影响范围/风险**：本次变更的核心功能（租户隔离）缺少测试覆盖，回归风险高。
- **修正建议**：补充以下测试用例：

```java
@Test
void testGetFileUrl_CrossTenant_ThrowsException() {
    when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
    // 使用不同的 appId
    BusinessException exception = assertThrows(BusinessException.class, () -> {
        fileAccessService.getFileUrl("other-app", "file-001", "123");
    });
    // 跨租户应返回 404（不暴露文件存在性）
}

@Test
void testGetFileDetail_CrossTenant_ThrowsException() {
    when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
    assertThrows(BusinessException.class, () -> {
        fileAccessService.getFileDetail("other-app", "file-001", "123");
    });
}

@Test
void testCanAccessFile_WithAppId_TenantIsolation() {
    assertFalse(fileAccessService.canAccessFile(publicFileRecord, "123", "other-app"));
    assertTrue(fileAccessService.canAccessFile(publicFileRecord, "123", "blog"));
}
```

#### [L2] FileAccessServicePropertyTest 中 Arbitrary 生成的 appId 集合有限，未覆盖跨租户

- **文件**：`file-service/src/test/java/com/architectcgz/file/application/service/FileAccessServicePropertyTest.java` 第 76、136、200 行
- **问题描述**：属性测试中 `getFileUrl()` 调用时使用 `fileRecord.getAppId()` 作为参数，始终与文件自身的 appId 匹配，无法触发跨租户拒绝路径。
- **影响范围/风险**：属性测试未覆盖租户隔离的反面场景。
- **修正建议**：新增一个属性测试验证跨租户访问必定被拒绝：

```java
@Property(tries = 100)
@Label("Property: 跨租户访问被拒绝")
void crossTenantAccessDenied(
        @ForAll("fileRecords") FileRecord fileRecord,
        @ForAll("appIds") String requestAppId
) {
    Assume.that(!requestAppId.equals(fileRecord.getAppId()));
    // ... 验证抛出异常
}
```

## 统计摘要

| 级别 | 数量 |
|------|------|
| 🔴 高 | 3 |
| 🟡 中 | 4 |
| 🟢 低 | 2 |
| 合计 | 9 |

## 总体评价

本次变更的设计方向正确——将分散的访问控制检查收敛到 `canAccessFile()` 是合理的重构。检查链的顺序（租户隔离 -> 文件状态 -> 访问级别）也符合安全最佳实践。

但存在三个高优先级问题需要修复：缓存命中路径完全绕过了新增的访问控制检查（H1），跨租户场景返回 403 而非 404 导致信息泄漏（H2），以及 `updateAccessLevel()` 未纳入统一校验体系（H3）。其中 H1 是最严重的——它使得本次变更新增的所有校验在缓存命中时完全无效。

建议优先修复 H1 和 H2，然后将 H3、M2、M3 一并处理，确保 `updateAccessLevel()` 的安全性与一致性。
