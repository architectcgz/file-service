# Implementation Plan: RustFS Integration Test

## Overview

本实现计划将创建一个完整的 RustFS 集成测试套件，验证文件上传服务与 RustFS 对象存储的集成。测试将使用真实的 S3 连接，验证文件上传、存储、访问和删除的完整流程。

## Tasks

- [x] 1. 创建测试辅助类和工具
  - [x] 1.1 创建 FileTestData 测试数据生成类
    - 实现创建文本文件、图片文件、大文件的方法
    - 实现生成随机字节数据的方法
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 1.2 创建 S3Verifier 验证类
    - 实现检查文件是否存在的方法
    - 实现获取文件内容的方法
    - 实现获取文件大小和 Content-Type 的方法
    - _Requirements: 1.3, 1.4_

  - [x] 1.3 创建 URLAccessVerifier URL 访问验证类
    - 实现通过 URL 下载文件的方法
    - 实现检查 URL 可访问性的方法
    - 实现获取 HTTP 状态码的方法
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 1.4 创建 TestContext 测试上下文类
    - 实现管理测试文件列表的功能
    - 实现测试数据清理的功能
    - _Requirements: 7.4_

- [x] 2. 创建测试配置
  - [x] 2.1 创建 RustFSTestConfig 配置类
    - 配置测试专用的 S3Client bean
    - 配置 HttpClient bean 用于 URL 访问测试
    - 不 Mock S3StorageService，使用真实实现
    - _Requirements: 1.5, 7.1, 7.2_

  - [x] 2.2 创建 application-rustfs-test.yml 配置文件
    - 配置 S3 存储类型和连接参数
    - 配置分片上传阈值和分片大小
    - 配置测试专用的 bucket 名称
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 3. 创建主测试类框架
  - [x] 3.1 创建 RustFSIntegrationTest 测试类
    - 添加 Spring Boot 测试注解和配置
    - 注入 MockMvc、Repositories、S3Verifier 等依赖
    - 实现 @BeforeAll 检查 RustFS 可用性
    - 实现 @BeforeEach 初始化测试上下文
    - 实现 @AfterEach 清理测试数据
    - _Requirements: 7.1, 7.4, 7.5_

- [-] 4. 实现基础上传测试
  - [x] 4.1 实现文本文件上传和访问测试
    - 上传文本文件到 RustFS
    - 验证返回的 fileId 和 URL
    - 使用 S3Verifier 验证文件存在
    - 使用 URLAccessVerifier 下载并验证内容
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3_

  - [x] 4.2 编写文本文件上传属性测试
    - **Property 1: 上传后文件存在性**
    - **Property 2: URL 访问一致性**
    - **Validates: Requirements 1.1, 1.3, 1.4, 2.1, 2.2, 2.3**

  - [x] 4.3 实现图片文件上传和 Content-Type 验证测试
    - 上传图片文件（JPEG）
    - 验证 Content-Type 正确保存
    - 验证文件内容完整
    - _Requirements: 3.1, 3.4_

  - [x] 4.4 编写图片文件 Content-Type 属性测试
    - **Property 3: 文件类型保持性**
    - **Validates: Requirements 3.4**

  - [x] 4.5 实现二进制文件上传和完整性验证测试
    - 上传二进制文件
    - 验证文件内容字节级一致
    - _Requirements: 3.3_

- [x] 5. 实现大文件分片上传测试
  - [x] 5.1 实现大文件分片上传测试
    - 创建超过阈值的大文件（15MB）
    - 验证使用了分片上传
    - 验证所有分片都上传成功
    - 验证完成后文件内容完整
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 5.2 编写大文件分片上传属性测试
    - **Property 4: 分片上传完整性**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

  - [x] 5.3 实现分片上传进度查询测试
    - 初始化分片上传
    - 上传部分分片
    - 查询上传进度
    - 验证进度信息正确
    - _Requirements: 4.1, 4.2_

- [x] 6. 实现文件删除测试
  - [x] 6.1 实现单文件删除测试
    - 上传文件到 RustFS
    - 删除文件
    - 验证文件从 RustFS 中移除
    - 验证 URL 不可访问
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 6.2 编写文件删除属性测试
    - **Property 5: 删除后不可访问性**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 6.3 实现引用计数删除测试
    - 上传相同内容的文件两次（触发去重）
    - 删除第一个文件记录
    - 验证存储对象仍然存在
    - 删除第二个文件记录
    - 验证存储对象被删除
    - _Requirements: 5.4_

  - [x] 6.4 编写引用计数删除属性测试
    - **Property 6: 引用计数删除正确性**
    - **Validates: Requirements 5.4**

- [x] 7. 实现多文件类型测试
  - [x] 7.1 实现多种文件类型上传测试
    - 上传文本、图片、视频等多种类型
    - 验证每种类型都能正确存储
    - 验证每种类型都能正确访问
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 8. 实现错误场景测试
  - [x] 8.1 实现 RustFS 不可用错误测试
    - 配置错误的 endpoint
    - 验证返回清晰的错误消息
    - _Requirements: 6.1_

  - [x] 8.2 实现无效 bucket 错误测试
    - 配置不存在的 bucket
    - 验证错误处理
    - _Requirements: 6.2_

  - [x] 8.3 实现网络错误处理测试
    - 模拟网络错误场景
    - 验证系统报告失败
    - _Requirements: 6.3, 6.4_

- [x] 9. Checkpoint - 确保所有核心测试通过
  - 运行所有测试用例
  - 验证测试数据被正确清理
  - 如有问题，询问用户

- [x] 10. 实现性能基准测试（可选）
  - [x] 10.1 实现上传性能测试
    - 测试不同大小文件的上传时间
    - 记录性能指标
    - 验证性能满足阈值
    - _Requirements: 8.1, 8.4_

  - [x] 10.2 实现下载性能测试
    - 测试通过 URL 下载的时间
    - 记录性能指标
    - _Requirements: 8.2_

  - [x] 10.3 添加性能测试日志和报告
    - 记录详细的性能指标
    - 生成性能测试报告
    - _Requirements: 8.3_

- [ ] 11. 创建测试文档和 README
  - [ ] 11.1 创建测试 README 文档
    - 说明如何运行测试
    - 说明测试环境要求
    - 说明如何使用 Docker 启动 RustFS
    - _Requirements: 7.5_

  - [ ] 11.2 添加 CI/CD 集成说明
    - 提供 GitHub Actions 配置示例
    - 说明如何在 CI/CD 中运行测试
    - _Requirements: 7.5_

- [ ] 12. Final Checkpoint - 完整测试验证
  - 在本地环境运行完整测试套件
  - 验证所有测试通过
  - 验证测试清理正常工作
  - 如有问题，询问用户

## Notes

- 每个任务都引用了具体的需求编号，确保可追溯性
- 测试使用真实的 RustFS 连接，不使用 Mock
- 性能测试（任务 10）标记为可选，可以根据需要决定是否实现
- 测试数据清理在每个测试后自动执行，确保测试隔离
