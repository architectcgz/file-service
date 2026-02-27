# StoragePathGenerator 使用指南

## 概述

`StoragePathGenerator` 是一个用于生成 S3 存储路径的工具类，确保文件路径的唯一性和可追溯性。

## 路径格式

生成的路径格式为：
```
{year}/{month}/{day}/{userId}/{fileId}.{ext}
```

**示例**：
```
2026/01/19/12345/01912345-6789-7abc-def0-123456789abc.jpg
```

## 核心特性

### 1. 时间有序的 UUID (UUIDv7)

使用 UUIDv7 生成文件 ID，具有以下优势：
- **时间有序**：UUID 包含时间戳前缀，天然支持按时间排序
- **全局唯一**：分布式环境下无需协调即可生成唯一 ID
- **高性能**：比传统 UUIDv4 更适合数据库索引

### 2. 日期分区

按年/月/日组织文件，便于：
- 文件管理和归档
- 按日期范围查询
- 存储空间规划

### 3. 用户隔离

每个用户的文件存储在独立目录下，便于：
- 用户数据隔离
- 按用户统计存储使用量
- 用户数据迁移

## 使用方法

### 基本用法

```java
@Autowired
private StoragePathGenerator pathGenerator;

// 生成存储路径
Long userId = 12345L;
String originalName = "my-photo.jpg";
String storagePath = pathGenerator.generateStoragePath(userId, originalName);
// 结果: 2026/01/19/12345/01912345-6789-7abc-def0-123456789abc.jpg
```

### 自定义扩展名

```java
// 当你已经知道扩展名时
String extension = "webp";
String storagePath = pathGenerator.generateStoragePathWithExtension(userId, extension);
// 结果: 2026/01/19/12345/01912345-6789-7abc-def0-123456789abc.webp
```

### 提取文件扩展名

```java
// 从文件名提取扩展名
String extension = pathGenerator.getExtension("my-photo.JPG");
// 结果: "jpg" (自动转换为小写)

String extension2 = pathGenerator.getExtension("archive.tar.gz");
// 结果: "gz" (只取最后一个扩展名)
```

## 集成示例

### 在 S3StorageService 中使用

```java
@Service
public class S3StorageService implements StorageService {
    
    @Autowired
    private StoragePathGenerator pathGenerator;
    
    @Autowired
    private S3Properties s3Properties;
    
    public String uploadImage(Long userId, byte[] data, String originalName) {
        // 生成存储路径
        String storagePath = pathGenerator.generateStoragePath(userId, originalName);
        
        // 上传到 S3
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(storagePath)
            .contentType(detectContentType(originalName))
            .build();
            
        s3Client.putObject(request, RequestBody.fromBytes(data));
        
        return storagePath;
    }
}
```

### 在 UploadApplicationService 中使用

```java
@Service
public class UploadApplicationService {
    
    @Autowired
    private StoragePathGenerator pathGenerator;
    
    @Autowired
    private StorageService storageService;
    
    @Autowired
    private FileRecordRepository fileRecordRepository;
    
    public UploadResponse uploadFile(Long userId, MultipartFile file) {
        // 生成存储路径
        String storagePath = pathGenerator.generateStoragePath(
            userId, 
            file.getOriginalFilename()
        );
        
        // 上传文件
        storageService.upload(file.getBytes(), storagePath, file.getContentType());
        
        // 创建文件记录
        FileRecord record = FileRecord.builder()
            .userId(userId)
            .originalName(file.getOriginalFilename())
            .storagePath(storagePath)
            .fileSize(file.getSize())
            .contentType(file.getContentType())
            .build();
            
        fileRecordRepository.save(record);
        
        return new UploadResponse(record.getId(), storageService.getUrl(storagePath));
    }
}
```

## 路径解析

如果需要从存储路径中提取信息：

```java
String storagePath = "2026/01/19/12345/01912345-6789-7abc-def0-123456789abc.jpg";
String[] parts = storagePath.split("/");

int year = Integer.parseInt(parts[0]);        // 2026
int month = Integer.parseInt(parts[1]);       // 01
int day = Integer.parseInt(parts[2]);         // 19
long userId = Long.parseLong(parts[3]);       // 12345
String fileNameWithExt = parts[4];            // 01912345-6789-7abc-def0-123456789abc.jpg
String fileId = fileNameWithExt.substring(0, fileNameWithExt.lastIndexOf('.')); // UUID
String extension = fileNameWithExt.substring(fileNameWithExt.lastIndexOf('.') + 1); // jpg
```

## 注意事项

### 1. 扩展名处理

- 扩展名自动转换为小写
- 多个点的文件名只取最后一个扩展名（如 `archive.tar.gz` → `gz`）
- 无扩展名的文件会生成以 `.` 结尾的路径

### 2. 唯一性保证

- UUIDv7 保证全局唯一性
- 即使同一用户在同一秒上传相同文件名，生成的路径也不同
- 适合高并发场景

### 3. 时区考虑

- 使用 `LocalDate.now()` 获取当前日期
- 基于服务器时区，建议统一使用 UTC 时区

### 4. 性能考虑

- UUIDv7 生成速度快（微秒级）
- 无需数据库查询或分布式协调
- 适合高吞吐量场景

## 测试覆盖

完整的单元测试覆盖：
- ✅ 路径格式验证
- ✅ 不同扩展名处理
- ✅ 无扩展名文件处理
- ✅ 多点文件名处理
- ✅ 路径唯一性验证
- ✅ 大小写转换
- ✅ 边界条件测试

运行测试：
```bash
mvn test -Dtest=StoragePathGeneratorTest -pl blog-upload
```

## 依赖

需要在 `pom.xml` 中添加 UUIDv7 依赖：

```xml
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>5.3.7</version>
</dependency>
```

## 相关文档

- [UUIDv7 规范](https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format)
- [uuid-creator 库文档](https://github.com/f4b6a3/uuid-creator)
- [S3 对象键命名最佳实践](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html)
