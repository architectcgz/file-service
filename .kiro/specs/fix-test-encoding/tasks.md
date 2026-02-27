# Implementation Plan: Fix Test Encoding

## Overview

手动逐个查看测试文件,识别并修复@DisplayName注解中的乱码。

## Tasks

- [x] 1. 查找所有包含乱码的测试文件
  - 搜索file-service/src/test/java目录下的所有测试文件
  - 识别包含乱码@DisplayName注解的文件
  - _Requirements: 1.1_

- [x] 2. 修复PresignedUrlServiceTest.java
  - 打开文件查看乱码的@DisplayName注解
  - 基于测试方法名推断正确的中文描述
  - 替换乱码文本为正确的中文
  - 保存文件(UTF-8编码)
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 3. 修复FileAccessServiceTest.java
  - 打开文件查看乱码的@DisplayName注解
  - 基于测试方法名推断正确的中文描述
  - 替换乱码文本为正确的中文
  - 保存文件(UTF-8编码)
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 4. 修复InstantUploadServiceTest.java
  - 打开文件查看乱码的@DisplayName注解
  - 基于测试方法名推断正确的中文描述
  - 替换乱码文本为正确的中文
  - 保存文件(UTF-8编码)
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 5. 修复S3StorageServiceTest.java
  - 打开文件查看乱码的@DisplayName注解
  - 基于测试方法名推断正确的中文描述
  - 替换乱码文本为正确的中文
  - 保存文件(UTF-8编码)
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 6. 修复其他包含乱码的测试文件
  - 查找并修复剩余的测试文件
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 7. 验证修复结果
  - 运行`mvn compile`验证所有文件可以编译
  - 检查修复后的@DisplayName注解是否可读
  - 使用Git查看所有修改
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

## Notes

- 每个任务都引用了具体的需求,确保可追溯性
- 修复时注意保持UTF-8编码
- 使用Git跟踪所有修改,便于回滚
