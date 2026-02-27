# Requirements Document

## Introduction

将文件服务系统中的 userId 字段从 Long 类型改为 String 类型，以支持更灵活的用户标识符格式（如 UUID、自定义字符串等）。

## Glossary

- **File_Service**: 文件上传和管理服务系统
- **UserContext**: 用户上下文，用于在请求处理过程中传递用户信息
- **FileRecord**: 文件记录领域模型
- **UploadTask**: 上传任务领域模型
- **Repository**: 数据访问层接口
- **Mapper**: MyBatis 数据库映射器
- **PO**: 持久化对象（Persistent Object）

## Requirements

### Requirement 1: 修改领域模型

**User Story:** 作为开发者，我希望领域模型中的 userId 字段使用 String 类型，以便支持多种用户标识符格式。

#### Acceptance Criteria

1. THE File_Service SHALL 将 FileRecord 模型中的 userId 字段类型从 Long 改为 String
2. THE File_Service SHALL 将 UploadTask 模型中的 userId 字段类型从 Long 改为 String
3. WHEN 模型被序列化或反序列化时，THE File_Service SHALL 正确处理 String 类型的 userId

### Requirement 2: 修改数据访问层

**User Story:** 作为开发者，我希望数据访问层能够正确处理 String 类型的 userId，以便与领域模型保持一致。

#### Acceptance Criteria

1. THE File_Service SHALL 更新 FileRecordRepository 接口中所有涉及 userId 的方法签名
2. THE File_Service SHALL 更新 UploadTaskRepository 接口中所有涉及 userId 的方法签名
3. THE File_Service SHALL 更新 StorageObjectRepository 接口中所有涉及 userId 的方法签名（如果存在）
4. THE File_Service SHALL 更新所有 Repository 实现类以使用 String 类型的 userId

### Requirement 3: 修改持久化对象

**User Story:** 作为开发者，我希望持久化对象（PO）中的 userId 字段使用 String 类型，以便与数据库和领域模型保持一致。

#### Acceptance Criteria

1. THE File_Service SHALL 将 FileRecordPO 中的 userId 字段类型从 Long 改为 String
2. THE File_Service SHALL 将 UploadTaskPO 中的 userId 字段类型从 Long 改为 String
3. THE File_Service SHALL 更新所有 PO 类中涉及 userId 的 getter 和 setter 方法

### Requirement 4: 修改 MyBatis Mapper

**User Story:** 作为开发者，我希望 MyBatis Mapper 能够正确映射 String 类型的 userId，以便数据库操作正常工作。

#### Acceptance Criteria

1. THE File_Service SHALL 更新 FileRecordMapper 中所有涉及 userId 的 SQL 参数类型
2. THE File_Service SHALL 更新 UploadTaskMapper 中所有涉及 userId 的 SQL 参数类型
3. THE File_Service SHALL 更新 StorageObjectMapper 中所有涉及 userId 的 SQL 参数类型（如果存在）
4. WHEN 执行数据库查询时，THE File_Service SHALL 正确处理 String 类型的 userId 参数

### Requirement 5: 修改工具类

**User Story:** 作为开发者，我希望工具类能够接受 String 类型的 userId，以便生成正确的存储路径。

#### Acceptance Criteria

1. THE File_Service SHALL 更新 StoragePathGenerator 类中所有涉及 userId 的方法签名
2. WHEN 生成存储路径时，THE File_Service SHALL 正确处理 String 类型的 userId

### Requirement 6: 修改测试代码

**User Story:** 作为开发者，我希望所有测试代码能够使用 String 类型的 userId，以便测试能够正常运行。

#### Acceptance Criteria

1. THE File_Service SHALL 更新所有单元测试中的 userId 变量类型
2. THE File_Service SHALL 更新所有集成测试中的 userId 变量类型
3. THE File_Service SHALL 更新 TestContext 类中的 userId 字段类型
4. THE File_Service SHALL 更新 FileTestData 类中所有涉及 userId 的方法
5. WHEN 运行测试时，THE File_Service SHALL 确保所有测试通过

### Requirement 7: 保持向后兼容

**User Story:** 作为系统维护者，我希望类型变更不会破坏现有功能，以便系统能够平稳过渡。

#### Acceptance Criteria

1. WHEN userId 类型改变后，THE File_Service SHALL 保持所有现有 API 的功能不变
2. WHEN 处理用户请求时，THE File_Service SHALL 正确解析和处理 String 类型的 userId
3. THE File_Service SHALL 确保所有业务逻辑在类型变更后仍然正常工作
