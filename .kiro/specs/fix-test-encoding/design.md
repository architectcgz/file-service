# Design Document: Fix Test Encoding

## Overview

本设计采用手动修复方式,逐个查看测试文件,识别乱码的@DisplayName注解,并基于测试方法名和上下文推断正确的中文描述进行替换。

## Architecture

简单的手动修复流程:

```
[手动查看文件] -> [识别乱码] -> [推断正确文本] -> [手动替换] -> [验证]
```

## Correctness Properties

*属性是系统应该在所有有效执行中保持为真的特征或行为。*

### Property 1: 编码一致性
*对于任何*修复后的测试文件,该文件应该使用UTF-8编码,且所有@DisplayName注解中的文本应该是有效的UTF-8中文或英文字符
**Validates: Requirements 1.4**

### Property 2: 语法保持性
*对于任何*被修复的测试文件,修复前后的Java语法结构应该保持一致,只有@DisplayName注解的字符串内容被修改
**Validates: Requirements 2.1, 2.2**

### Property 3: 编译正确性
*对于任何*被修复的文件,文件应该能够正常编译
**Validates: Requirements 2.3**

## Error Handling

- 如果无法推断正确的中文描述,保留原文或使用英文描述
- 修复后运行编译验证,确保没有语法错误
- 使用Git跟踪所有修改,便于回滚

## Testing Strategy

### 手动验证

1. 修复每个文件后,检查@DisplayName注解是否可读
2. 运行`mvn compile`验证文件可以编译
3. 运行测试验证功能未被破坏

## Implementation Notes

### 修复策略

1. **查看文件**: 打开每个测试文件
2. **识别乱码**: 找到包含乱码字符的@DisplayName注解
3. **推断描述**: 基于测试方法名(如`shouldUploadFileSuccessfully` -> "应该成功上传文件")推断中文描述
4. **手动替换**: 将乱码替换为正确的中文
5. **保存文件**: 确保使用UTF-8编码保存

### 需要修复的文件

基于之前的搜索结果,主要需要修复:
- `PresignedUrlServiceTest.java`
- `FileAccessServiceTest.java`
- `InstantUploadServiceTest.java`
- `S3StorageServiceTest.java`
- 其他包含乱码的测试文件
