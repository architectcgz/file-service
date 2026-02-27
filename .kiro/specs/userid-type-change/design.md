# Design Document: UserId Type Change

## Overview

本设计文档描述如何将文件服务系统中的 userId 字段从 Long 类型改为 String 类型。这个改动将影响多个层次，包括领域模型、数据访问层、持久化对象、MyBatis Mapper 和测试代码。

改动的主要目标是：
- 支持更灵活的用户标识符格式（UUID、自定义字符串等）
- 保持系统功能的完整性和一致性
- 确保所有测试通过

## Architecture

系统采用分层架构：
- **Domain Layer**: 领域模型（FileRecord, UploadTask）
- **Repository Layer**: 数据访问接口和实现
- **Infrastructure Layer**: 持久化对象（PO）、Mapper、工具类
- **Test Layer**: 单元测试和集成测试

类型变更将从领域层开始，逐层向下传播到基础设施层，最后更新测试代码。

## Components and Interfaces

### 1. Domain Models

**FileRecord.java**
- 将 `private Long userId` 改为 `private String userId`
- 所有使用 userId 的方法保持不变（getter/setter 自动更新）

**UploadTask.java**
- 将 `private Long userId` 改为 `private String userId`
- 所有使用 userId 的方法保持不变

### 2. Repository Interfaces

**FileRecordRepository.java**
- 更新方法签名：`Optional<FileRecord> findByUserIdAndFileHash(String appId, String userId, String fileHash)`

**UploadTaskRepository.java**
- 更新方法签名：
  - `Optional<UploadTask> findByUserIdAndFileHash(String appId, String userId, String fileHash)`
  - `List<UploadTask> findByUserId(String appId, String userId, int limit)`

**StorageObjectRepository.java**
- 检查是否有涉及 userId 的方法，如有则更新

### 3. Repository Implementations

**FileRecordRepositoryImpl.java**
- 更新所有涉及 userId 的方法实现
- 确保正确传递 String 类型的 userId 给 Mapper

**UploadTaskRepositoryImpl.java**
- 更新所有涉及 userId 的方法实现

**StorageObjectRepositoryImpl.java**
- 如有涉及 userId 的方法，则更新

### 4. Persistent Objects (PO)

**FileRecordPO.java**
- 将 `private Long userId` 改为 `private String userId`

**UploadTaskPO.java**
- 将 `private Long userId` 改为 `private String userId`

### 5. MyBatis Mappers

**FileRecordMapper.java** 和对应的 XML
- 更新所有涉及 userId 的参数类型：`@Param("userId") String userId`
- 更新 XML 中的参数类型（如果有显式声明）

**UploadTaskMapper.java** 和对应的 XML
- 更新所有涉及 userId 的参数类型

**StorageObjectMapper.java** 和对应的 XML
- 如有涉及 userId 的方法，则更新

### 6. Utility Classes

**StoragePathGenerator.java**
- 更新方法签名：
  - `String generateStoragePath(String appId, String userId, String fileType, String originalFilename)`
  - `String generateStoragePathWithExtension(String appId, String userId, String fileType, String extension)`

### 7. Test Classes

**TestContext.java**
- 将 `private Long userId` 改为 `private String userId`
- 更新构造函数和 getter/setter

**FileTestData.java**
- 更新所有涉及 userId 的方法和常量

**所有测试类**
- 将 userId 变量从 Long 改为 String
- 更新测试数据（如 `12345L` 改为 `"12345"` 或 `"user-123"`）

## Data Models

### Domain Model Changes

```java
// Before
public class FileRecord {
    private Long userId;
}

// After
public class FileRecord {
    private String userId;
}
```

### PO Changes

```java
// Before
public class FileRecordPO {
    private Long userId;
}

// After
public class FileRecordPO {
    private String userId;
}
```

### Test Data Changes

```java
// Before
Long userId = 12345L;
TestContext context = new TestContext("app-id", 12345L);

// After
String userId = "12345";
TestContext context = new TestContext("app-id", "12345");
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Type Consistency

*For any* FileRecord or UploadTask instance, the userId field should be of type String and should be correctly serialized/deserialized.

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Repository Method Compatibility

*For any* repository method that accepts userId as a parameter, it should accept String type and correctly pass it to the underlying Mapper.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

### Property 3: Database Mapping Correctness

*For any* database operation involving userId, the MyBatis Mapper should correctly map String type userId to the database column.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4**

### Property 4: Path Generation Consistency

*For any* valid appId, userId (String), fileType, and filename, the StoragePathGenerator should generate a valid storage path containing the userId.

**Validates: Requirements 5.1, 5.2**

### Property 5: Test Data Validity

*For any* test case, when using String type userId, the test should execute successfully and produce the same logical results as before the type change.

**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**

### Property 6: API Functional Equivalence

*For any* API endpoint that processes userId, the functionality should remain unchanged after the type conversion from Long to String.

**Validates: Requirements 7.1, 7.2, 7.3**

## Error Handling

类型变更本身不引入新的错误处理逻辑，但需要注意：

1. **空值处理**: String 类型的 userId 可以为 null，需要确保现有的空值检查仍然有效
2. **格式验证**: 如果需要对 userId 格式进行验证（如长度、字符集），应在适当的层次添加
3. **数据库约束**: 确保数据库表中的 user_id 列类型支持 String（VARCHAR）

## Testing Strategy

### Unit Tests

- 测试领域模型的 getter/setter 方法
- 测试 Repository 方法的参数传递
- 测试 StoragePathGenerator 的路径生成逻辑
- 测试特定的边界情况（空字符串、特殊字符等）

### Integration Tests

- 测试完整的文件上传流程（使用 String 类型的 userId）
- 测试文件去重功能
- 测试多应用隔离功能
- 测试 RustFS 集成

### Property-Based Tests

如果现有代码中有 property-based tests，需要更新测试数据生成器以生成 String 类型的 userId。

### Test Configuration

- 所有测试应运行至少 100 次迭代（如果使用 property-based testing）
- 每个测试应明确标注其验证的设计属性
- 使用描述性的测试名称

## Implementation Notes

1. **变更顺序**: 
   - 先修改领域模型
   - 然后修改 PO 和 Repository 接口
   - 接着修改 Repository 实现和 Mapper
   - 最后修改工具类和测试代码

2. **数据库迁移**: 
   - 如果数据库中已有数据，可能需要数据迁移脚本
   - 本设计假设数据库列类型已经是 VARCHAR 或可以自动转换

3. **测试数据**: 
   - 使用有意义的字符串作为测试 userId（如 "user-123", "test-user"）
   - 保持测试数据的一致性和可读性

4. **编译检查**: 
   - 利用 Java 的类型系统，编译器会帮助发现所有需要修改的地方
   - 修改后应确保项目能够成功编译
