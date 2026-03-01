# File-Service 设计问题修复方案

> 基于代码审查梳理，按严重性分级，每个问题包含：问题描述、影响、修复方案、涉及文件。

---

## 高优先级

### 1. ~~事务内包含 S3 操作（长事务风险）~~ ✅ 已完成

**问题描述**

`UploadApplicationService.uploadImage()`（第 60 行）、`uploadFile()`（第 205 行）、`DirectUploadService.completeDirectUpload()`（第 307 行）、`MultipartUploadService.completeUpload()`（第 209 行）等方法将 S3 操作放在 `@Transactional` 内。S3 网络调用耗时不可控（100ms~数秒），期间数据库连接被占用。

**影响**

- 高并发下数据库连接池耗尽
- S3 超时导致事务长时间挂起
- S3 上传成功但数据库提交失败时，产生 S3 孤立文件

**修复方案**

将 S3 操作移到事务外，采用「先 S3，后短事务写库」的模式。失败时通过补偿清理 S3 孤立文件。

以 `uploadImage()` 为例：

```java
// 修改前：整个方法在 @Transactional 内
@Transactional(rollbackFor = Exception.class)
public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
    // ... 校验 ...
    imageUrl = storageService.upload(processedImage, imagePath, contentType);  // S3 操作在事务内
    storageObjectRepository.save(storageObject);
}

// 修改后：S3 操作移到事务外
public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
    // 1. 校验（无事务）
    fileTypeValidator.validate(file);

    // 2. S3 上传（无事务，不占数据库连接）
    String imageUrl = storageService.upload(processedImage, imagePath, contentType);

    // 3. 短事务写库
    try {
        return saveUploadRecord(appId, userId, imageUrl, storageObject, fileRecord);
    } catch (Exception e) {
        // 4. 数据库失败时补偿清理 S3
        cleanupS3Quietly(imagePath);
        throw e;
    }
}

@Transactional(rollbackFor = Exception.class)
private UploadResult saveUploadRecord(...) {
    storageObjectRepository.save(storageObject);
    fileRecordRepository.save(fileRecord);
    return buildResult(fileRecord);
}

private void cleanupS3Quietly(String path) {
    try {
        storageService.delete(path);
    } catch (Exception e) {
        log.warn("S3 补偿清理失败，路径: {}，需人工处理", path, e);
    }
}
```

同样的模式应用到 `uploadFile()`、`completeDirectUpload()`、`completeUpload()`。

**涉及文件**

- `UploadApplicationService.java`：第 60、205 行
- `DirectUploadService.java`：第 307 行
- `MultipartUploadService.java`：第 209 行

---

### 2. ~~引用计数的竞态条件~~ ✅ 已完成

**问题描述**

`UploadApplicationService`（第 102-111 行）和 `InstantUploadService`（第 67-105 行）中，秒传逻辑先 `findByFileHash()` 再 `incrementReferenceCount()`，两步不是原子操作。并发请求可能同时读到相同的 StorageObject，导致引用计数不准确。

**影响**

- 引用计数偏高：文件永远无法被物理删除，存储泄漏
- 引用计数偏低：文件被误删，其他用户的 FileRecord 指向已删除的 S3 对象

**修复方案**

在 Mapper 层使用 SQL 原子更新，避免先读后写的竞态窗口。

```sql
-- StorageObjectMapper.xml：原子化「查找并增加引用计数」
-- 用一条 UPDATE 替代 SELECT + UPDATE 两步操作
UPDATE storage_objects
SET reference_count = reference_count + 1,
    updated_at = NOW()
WHERE app_id = #{appId}
  AND file_hash = #{fileHash}
  AND hash_algorithm = #{hashAlgorithm}
```

```java
// StorageObjectRepository 接口新增方法
/**
 * 原子化查找并增加引用计数
 * @return 受影响行数，0 表示未找到匹配记录（非秒传），1 表示秒传成功
 */
int findByHashAndIncrementCount(String appId, String fileHash, String hashAlgorithm);

// 应用层调用方式
int affected = storageObjectRepository.findByHashAndIncrementCount(appId, fileHash, algorithm);
if (affected > 0) {
    // 秒传命中，查询 StorageObject 信息用于创建 FileRecord
    StorageObject storageObject = storageObjectRepository.findByFileHash(appId, fileHash, algorithm)
            .orElseThrow();
    // 创建 FileRecord ...
} else {
    // 未命中，走正常上传流程
}
```

同理，`decrementReferenceCount` 也应使用原子 SQL：

```sql
UPDATE storage_objects
SET reference_count = reference_count - 1,
    updated_at = NOW()
WHERE id = #{id}
  AND reference_count > 0
```

**涉及文件**

- `StorageObjectMapper.xml`：新增原子更新 SQL
- `StorageObjectRepository.java`：新增 `findByHashAndIncrementCount()` 接口
- `StorageObjectRepositoryImpl.java`：实现新接口
- `UploadApplicationService.java`：第 102-111 行
- `InstantUploadService.java`：第 67-105 行

---

### 3. S3 与数据库状态不一致

**问题描述**

`deleteFile()`（第 322-368 行）先更新数据库状态为 DELETED，再删除 S3 对象。如果 S3 删除失败，数据库已标记删除但 S3 文件仍在，产生无法追踪的孤立文件。上传场景同理——S3 成功但数据库写入失败也会产生孤立文件。

**影响**

- 存储空间持续泄漏，无法自动回收
- 数据库与 S3 状态不一致，排查困难

**修复方案**

方案一：调整 `deleteFile()` 操作顺序为「先删 S3，再更新数据库」：

```java
public void deleteFile(String appId, String fileRecordId, String userId) {
    // 1. 查询并校验
    FileRecord fileRecord = fileRecordRepository.findById(fileRecordId).orElseThrow();
    StorageObject storageObject = storageObjectRepository.findById(fileRecord.getStorageObjectId()).orElseThrow();

    // 2. 判断是否需要删除 S3 对象（引用计数将归零）
    boolean shouldDeleteS3 = storageObject.getReferenceCount() <= 1;

    // 3. 先删 S3（失败则整个操作中止，数据库状态不变）
    if (shouldDeleteS3) {
        storageService.delete(storageObject.getStoragePath());
    }

    // 4. 短事务更新数据库
    deleteFileInTransaction(fileRecordId, storageObject.getId(), shouldDeleteS3);
}

@Transactional(rollbackFor = Exception.class)
private void deleteFileInTransaction(String fileRecordId, String storageObjectId, boolean deleteStorageObject) {
    fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);
    storageObjectRepository.decrementReferenceCount(storageObjectId);
    if (deleteStorageObject) {
        storageObjectRepository.deleteById(storageObjectId);
    }
}
```

方案二：引入孤立文件清理定时任务作为兜底：

```java
/**
 * 定时扫描 S3 中存在但数据库无记录的孤立文件
 * 建议每天凌晨执行一次
 */
@Scheduled(cron = "0 0 3 * * *")
public void cleanupOrphanedS3Objects() {
    // 1. 列出 S3 中最近 N 天创建的对象
    // 2. 与数据库 storage_objects 表比对
    // 3. 数据库中不存在的 S3 对象标记为孤立
    // 4. 孤立超过 24 小时的对象执行删除
}
```

两个方案建议同时实施：方案一减少不一致发生的概率，方案二作为最终一致性兜底。

**涉及文件**

- `UploadApplicationService.java`：第 322-368 行
- 新增 `OrphanedObjectCleanupScheduler.java`

---

## 中优先级

### 4. 分片上传幂等性不完整

**问题描述**

`MultipartUploadService.uploadPart()`（第 174-182 行）检测到重复分片时直接抛出 `BusinessException("分片已上传，请勿重复提交")`。客户端因网络超时重试同一分片时会收到异常，无法拿到原始 ETag 来完成后续的 `completeUpload()`。

**影响**

- 客户端重试失败，分片上传流程中断
- 断点续传体验差

**修复方案**

重复分片时返回已有的 ETag，而非抛异常：

```java
// 修改前
if (completedPartNumbers.contains(partNumber)) {
    throw new BusinessException("分片已上传，请勿重复提交");
}

// 修改后
if (completedPartNumbers.contains(partNumber)) {
    UploadPart existingPart = uploadPartRepository.findByTaskIdAndPartNumber(taskId, partNumber);
    log.info("分片 {} 已上传，返回已有 ETag 实现幂等", partNumber);
    return UploadPartResult.builder()
            .partNumber(partNumber)
            .etag(existingPart.getEtag())
            .build();
}
```

**涉及文件**

- `MultipartUploadService.java`：第 174-182 行
- `UploadPartRepository.java`：确认 `findByTaskIdAndPartNumber()` 方法存在

---

### 5. 缓存一致性缺口

**问题描述**

`FileAccessService.getFileUrl()`（第 56-115 行）缓存了公开文件的 URL（key: `file:{fileId}:url`）。但 `updateAccessLevel()`（第 242-264 行）将文件从 PUBLIC 改为 PRIVATE 时，没有清除对应缓存。修改后旧的公开 URL 仍会从缓存返回，绕过了访问控制。

**影响**

- 文件改为 PRIVATE 后，在缓存 TTL（3600s）内仍可通过旧 URL 公开访问
- 访问控制形同虚设

**修复方案**

在所有修改文件状态的操作中清除缓存：

```java
// FileAccessService.updateAccessLevel()
public void updateAccessLevel(String appId, String fileId, String userId, AccessLevel newLevel) {
    // ... 现有更新逻辑 ...

    // 清除缓存（新增）
    clearUrlCache(fileId);
}

// 同样在 deleteFile() 中也要清除（确认是否已有）
```

**涉及文件**

- `FileAccessService.java`：第 242-264 行

---

### 6. 统计查询全量加载内存（OOM 风险）

**问题描述**

`FileManagementService.getStorageStatistics()`（第 160-205 行）使用 `query.setSize(Integer.MAX_VALUE)` 将所有文件记录加载到内存，再用 Java Stream 做聚合计算。

**影响**

- 文件量达到百万级时直接 OOM
- 即使不 OOM，也会造成长时间 GC 停顿

**修复方案**

将聚合计算下推到 SQL 层：

```sql
-- FileRecordMapper.xml 新增聚合查询
<select id="selectStorageStatistics" resultType="map">
    SELECT
        COUNT(*) AS total_files,
        COALESCE(SUM(file_size), 0) AS total_storage_bytes,
        COUNT(CASE WHEN access_level = 'PUBLIC' THEN 1 END) AS public_files,
        COUNT(CASE WHEN access_level = 'PRIVATE' THEN 1 END) AS private_files
    FROM file_records
    WHERE status != 'DELETED'
      <if test="appId != null">AND app_id = #{appId}</if>
</select>

<select id="selectFileCountByContentType" resultType="map">
    SELECT content_type, COUNT(*) AS file_count
    FROM file_records
    WHERE status != 'DELETED'
      <if test="appId != null">AND app_id = #{appId}</if>
    GROUP BY content_type
</select>
```

```java
// FileManagementService 修改
public StorageStatistics getStorageStatistics(String appId) {
    Map<String, Object> stats = fileRecordMapper.selectStorageStatistics(appId);
    List<Map<String, Object>> byType = fileRecordMapper.selectFileCountByContentType(appId);
    return StorageStatistics.fromAggregation(stats, byType);
}
```

**涉及文件**

- `FileManagementService.java`：第 160-205 行
- `FileRecordMapper.xml`：新增聚合查询
- `FileRecordMapper.java`：新增接口方法

---

### 7. 图片上传内存峰值过高

**问题描述**

`UploadApplicationService.uploadImage()`（第 67 行）通过 `file.getBytes()` 一次性将整个文件读入内存，随后压缩、WebP 转换、缩略图生成各产生一份副本。内存峰值约为文件大小的 3-4 倍。

**影响**

- 上传 50MB 图片时内存占用可达 150-200MB
- 多个并发上传可能触发 OOM

**修复方案**

用临时文件替代内存 byte[]，处理完后及时释放：

```java
// 修改前
byte[] fileData = file.getBytes();  // 全部读入内存
byte[] processedImage = imageProcessor.process(fileData, config);
byte[] thumbnail = imageProcessor.generateThumbnail(fileData, thumbConfig);

// 修改后：使用临时文件
Path tempFile = Files.createTempFile("upload-", ".tmp");
try {
    file.transferTo(tempFile);  // 写入临时文件，不占堆内存

    // 图片处理直接读写文件，避免 byte[] 中间态
    Path processedFile = imageProcessor.processToFile(tempFile, config);
    Path thumbnailFile = imageProcessor.generateThumbnailToFile(tempFile, thumbConfig);

    // 流式上传到 S3
    storageService.upload(processedFile, imagePath, contentType);
    storageService.upload(thumbnailFile, thumbnailPath, "image/jpeg");
} finally {
    Files.deleteIfExists(tempFile);
}
```

如果改动量太大，退而求其次可以加并发上传限流（Semaphore）控制同时处理的图片数量。

**涉及文件**

- `UploadApplicationService.java`：第 67 行
- `ImageProcessor.java`：新增基于文件的处理方法
- `StorageService.java`：确认支持 Path/InputStream 上传

---

### 8. PresignedUrlService.confirmUpload() 元数据未实现

**问题描述**

`PresignedUrlService.confirmUpload()`（第 159、183 行）中 `fileSize` 硬编码为 `0L`，`contentType` 硬编码为 `"application/octet-stream"`，代码中有 TODO 注释标记但未实现。

**影响**

- 租户配额统计不准确（fileSize=0 导致用量永远为 0）
- 文件类型信息丢失，影响后续的类型过滤和展示

**修复方案**

在 `confirmUpload()` 中通过 S3 HeadObject 获取真实元数据：

```java
// 修改前
.fileSize(0L)                              // TODO: 从 S3 HeadObject 获取
.contentType("application/octet-stream")   // TODO: 从 S3 HeadObject 获取

// 修改后
HeadObjectResponse head = s3StorageService.headObject(request.getStoragePath());
long fileSize = head.contentLength();
String contentType = head.contentType();

// 构建 StorageObject 时使用真实值
.fileSize(fileSize)
.contentType(contentType)
```

需要在 `StorageService` 接口中新增 `headObject()` 方法（如果尚未存在）。

**涉及文件**

- `PresignedUrlService.java`：第 159、183 行
- `StorageService.java` / `S3StorageService.java`：确认或新增 `headObject()` 方法

---

### 9. 访问控制检查不完整

**问题描述**

`FileAccessService.canAccessFile()`（第 219-231 行）仅检查 AccessLevel 和 userId，未校验：
- appId 归属（跨租户访问）
- 文件状态（DELETED 状态的文件仍可被访问）
- 租户状态（SUSPENDED/DELETED 租户的文件仍可被访问）

**影响**

- 跨租户数据泄漏风险
- 已删除文件仍可被访问

**修复方案**

补全访问控制检查链：

```java
// 修改前
public boolean canAccessFile(FileRecord file, String requestUserId) {
    if (file.getAccessLevel() == AccessLevel.PUBLIC) {
        return true;
    }
    if (file.getAccessLevel() == AccessLevel.PRIVATE) {
        return file.getUserId() != null && file.getUserId().equals(requestUserId);
    }
    return false;
}

// 修改后
public boolean canAccessFile(FileRecord file, String requestUserId, String appId) {
    // 1. 租户隔离
    if (!file.belongsToApp(appId)) {
        return false;
    }
    // 2. 文件状态
    if (file.isDeleted()) {
        return false;
    }
    // 3. 访问级别
    if (file.getAccessLevel() == AccessLevel.PUBLIC) {
        return true;
    }
    if (file.getAccessLevel() == AccessLevel.PRIVATE) {
        return file.getUserId() != null
                && file.getUserId().equals(requestUserId);
    }
    return false;
}
```

注意：调用方也需要同步传入 `appId` 参数。

**涉及文件**

- `FileAccessService.java`：第 219-231 行及所有调用处

---

## 低优先级

### 10. 硬编码魔法数字

**问题描述**

`DirectUploadService`（第 278、284 行）中预签名 URL 过期时间硬编码为 `3600`，未通过配置注入。`MultipartUploadService.completeUpload()`（第 278 行）中 contentType 硬编码为 `"application/octet-stream"`。

**影响**

- 修改过期时间需要改代码重新部署
- 与已有的 `AccessProperties.presignedUrlExpireSeconds` 配置项重复定义

**修复方案**

将硬编码值替换为已有的配置注入：

```java
// DirectUploadService.java
// 修改前
String presignedUrl = s3StorageService.generatePresignedUploadPartUrl(
        task.getStoragePath(), task.getUploadId(), partNumber, 3600);

// 修改后：注入已有配置
@RequiredArgsConstructor
public class DirectUploadService {
    private final AccessProperties accessProperties;

    // 使用配置值
    String presignedUrl = s3StorageService.generatePresignedUploadPartUrl(
            task.getStoragePath(), task.getUploadId(), partNumber,
            accessProperties.getPresignedUrlExpireSeconds());
}
```

**涉及文件**

- `DirectUploadService.java`：第 278、284 行
- `MultipartUploadService.java`：第 278 行

---

### 11. 重复的错误消息字符串

**问题描述**

`"上传任务不存在"` 等错误消息在 `MultipartUploadService`、`DirectUploadService` 等多个 Service 中重复出现。修改措辞需要逐个文件搜索替换。

**影响**

- 代码可维护性差
- 未来做国际化时改动面大

**修复方案**

新增错误消息常量类，收敛所有重复字符串：

```java
public final class FileServiceErrorMessages {
    private FileServiceErrorMessages() {}

    public static final String UPLOAD_TASK_NOT_FOUND = "上传任务不存在";
    public static final String FILE_NOT_FOUND = "文件不存在";
    public static final String FILE_ALREADY_DELETED = "文件已删除";
    public static final String ACCESS_DENIED = "无权访问该文件";
    public static final String PART_ALREADY_UPLOADED = "分片已上传";
    public static final String INVALID_FILE_TYPE = "不支持的文件类型";
    public static final String FILE_SIZE_EXCEEDED = "文件大小超出限制";
    public static final String TENANT_NOT_FOUND = "租户不存在";
    public static final String TENANT_SUSPENDED = "租户已被暂停";
}
```

各 Service 中替换硬编码字符串为常量引用。

**涉及文件**

- 新增 `common/constant/FileServiceErrorMessages.java`
- `MultipartUploadService.java`、`DirectUploadService.java`、`UploadApplicationService.java` 等所有抛 BusinessException 的位置

---

### 12. 领域模型与 Repository 操作路径不一致

**问题描述**

`StorageObject` 领域模型中定义了 `incrementReferenceCount()` / `decrementReferenceCount()` 方法（第 75-92 行），但应用层直接调用 `storageObjectRepository.incrementReferenceCount(id)` 绕过了领域模型。两条操作路径并存，业务规则分散。

**影响**

- 领域模型中的业务逻辑形同虚设
- 新开发者不确定该走哪条路径，容易引入不一致

**修复方案**

统一走 Repository 的原子 SQL 操作路径（与问题 2 的修复方案一致），移除领域模型中的内存操作方法，避免误用：

```java
// StorageObject.java：移除内存操作方法
// 删除 incrementReferenceCount() 和 decrementReferenceCount()
// 引用计数的变更统一通过 Repository 的原子 SQL 完成

// 应用层统一调用
storageObjectRepository.incrementReferenceCount(storageObjectId);  // 原子 SQL
storageObjectRepository.decrementReferenceCount(storageObjectId);  // 原子 SQL
```

**涉及文件**

- `StorageObject.java`：第 75-92 行（移除内存操作方法）
- 确认所有调用方统一走 Repository
