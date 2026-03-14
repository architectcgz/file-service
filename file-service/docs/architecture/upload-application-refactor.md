# 上传应用层重构说明

## 当前目标

参考 `zhicore-content`、`zhicore-comment` 的应用层组织方式，先把上传域从“单个超大 Service”拆成：

- façade：给 controller 提供稳定入口
- command service：处理写操作用例
- query service：处理读操作用例
- 明确职责组件：按 validator / factory / storage / assembler / file / image / transaction 等语义拆分协作对象

本轮先收敛：

- `DirectUploadService`
- `MultipartUploadService`
- `UploadApplicationService`
- `PresignedUrlService`
- `FileAccessService`
- `FileManagementService`
- `TenantManagementService`
- `FileTypeValidator`
- `InstantUploadService`
- `AccessLevelChangeTransactionHelper`
- `FileDeleteTransactionHelper`
- `AuditLogService`
- `UploadPartTransactionHelper`
- `UploadTransactionHelper`

## 当前结构

### 直传上传

- `DirectUploadService`
  - 仅作为 façade，对外保持原方法签名
- `direct/command/DirectUploadInitCommandService`
  - 初始化直传
  - 秒传判断
  - 续传任务复用
- `direct/command/DirectUploadCompleteCommandService`
  - 直传完成校验
  - 以对象存储中的 authoritative parts 为准完成合并
  - 元数据落库
- `direct/query/DirectUploadProgressQueryService`
  - 查询上传进度
- `direct/query/DirectUploadPartUrlQueryService`
  - 生成分片预签名上传地址
- `direct/validator/DirectUploadTaskValidator`
  - 任务访问校验
  - 过期与状态校验
  - `fileHash` 与分片完成请求校验
- `direct/factory/DirectUploadObjectFactory`
  - 直传 storage path 生成
  - `FileRecord` / `StorageObject` 构造
- `direct/assembler/DirectUploadPartResponseAssembler`
  - 已完成分片编号装配
  - 分片响应 DTO 装配
- `direct/storage/DirectUploadStorageService`
  - bucket 解析
  - multipart create / abort / complete
  - S3 authoritative parts 读取
  - 预签名分片上传地址与公开 URL 生成
  - 存储清理收口

### 分片上传

- `MultipartUploadService`
  - 仅作为 façade，对外保持原方法签名
- `multipart/command/MultipartUploadInitCommandService`
  - 初始化分片任务
  - 续传任务的已完成分片查询走 `uploadpart/query`
- `multipart/command/MultipartPartUploadCommandService`
  - 单分片上传
  - 分片级并发锁与幂等返回
- `multipart/command/MultipartUploadCompleteCommandService`
  - 完成合并
  - 元数据落库
- `multipart/command/MultipartUploadAbortCommandService`
  - 中止上传
- `multipart/query/MultipartUploadProgressQueryService`
  - 查询进度
  - 已完成分片计数走 `uploadpart/query`
- `multipart/query/MultipartUploadTaskQueryService`
  - 查询任务列表
- `multipart/validator/MultipartUploadTaskValidator`
  - 任务访问校验
  - `fileHash` 校验
- `multipart/factory/MultipartUploadObjectFactory`
  - 分片上传 storage path 生成
  - `StorageObject` / `FileRecord` 构造
- `multipart/storage/MultipartUploadStorageService`
  - bucket 解析
  - multipart create / uploadPart / abort / complete
  - 存储清理收口

### 表单上传

- `UploadApplicationService`
  - 仅作为 façade，对外保留图片上传、文件上传、删除入口
- `upload/command/ImageUploadCommandService`
  - 图片处理
  - 上传补偿
  - 秒传与新文件写库
- `upload/command/FileUploadCommandService`
  - 普通文件上传
  - 去重与新文件落库
- `upload/command/FileDeleteCommandService`
  - 用户删除文件
  - 存储删除与数据库短事务协同
- `upload/factory/UploadObjectFactory`
  - storage path 生成
  - `FileRecord` / `StorageObject` / `UploadResult` 构造
- `upload/storage/UploadStorageService`
  - bucket 解析
  - 表单上传与临时文件上传收口
  - 补偿清理与公开 URL 解析
- `upload/file/UploadFileHashService`
  - 内存文件与临时文件 MD5 计算
- `upload/file/UploadTempFileService`
  - 临时文件头读取
  - 临时文件静默删除
- `upload/image/UploadImageFormatResolver`
  - 处理后图片扩展名解析
  - 处理后图片 Content-Type 解析

### 预签名直传

- `PresignedUrlService`
  - 仅作为 façade，对外保留 `presign`、`confirm`
- `presigned/query/PresignedUploadUrlQueryService`
  - 申请预签名上传地址
  - access level 解析
  - 秒传前置检查
- `presigned/query/PresignedStorageObjectQueryService`
  - bucket 维度的共享对象查询
- `presigned/command/PresignedUploadConfirmCommandService`
  - 上传确认
  - 读取对象真实元数据
  - 去重与 `FileRecord` 落库
- `presigned/validator/PresignedUploadAccessResolver`
  - access level 解析
- `presigned/factory/PresignedUploadObjectFactory`
  - 存储路径生成
  - `StorageObject` / `FileRecord` 构造
- `presigned/storage/PresignedUploadStorageService`
  - bucket 解析
  - PUT 预签名地址生成
  - 对象元数据读取
  - 公有 URL / 私有预签名访问 URL 解析

### 文件访问

- `FileAccessService`
  - 仅作为 façade，对外保留 URL 查询、详情查询、访问级别修改
- `fileaccess/query/FileAccessRecordQueryService`
  - `FileRecord` 查询
  - `StorageObject` 查询
  - 存储桶归属解析
- `fileaccess/query/FileUrlQueryService`
  - 文件访问 URL 查询
  - 公开 URL 缓存
  - 私有文件签名 URL 生成
- `fileaccess/query/FileDetailQueryService`
  - 文件详情查询
- `fileaccess/command/FileAccessLevelCommandService`
  - 访问级别切换
  - 跨桶复制与 storage rebind
  - 事务提交后缓存清理与旧对象删除
- `fileaccess/validator/FileAccessValidator`
  - 文件可访问性校验
  - 访问级别修改权限校验
- `fileaccess/factory/FileAccessObjectFactory`
  - 文件详情 DTO 构造
  - 跨桶复制后的 `StorageObject` 构造
- `fileaccess/storage/FileAccessStorageService`
  - bucket 解析
  - 公开 URL / 预签名 URL 生成
  - URL 缓存读写
  - copy / cleanup / afterCommit 清理收口
- `fileaccess/transaction/AccessLevelOnlyTransactionService`
  - 纯访问级别更新短事务
- `fileaccess/transaction/AccessLevelStorageRebindTransactionService`
  - copied storage 保存
  - storage binding 更新
  - 源对象引用计数扣减
- `fileaccess/transaction/mutation/FileAccessRecordMutationService`
  - 访问级别更新
  - storage binding 与 access level 联合更新
- `fileaccess/transaction/persistence/FileAccessStoragePersistenceService`
  - copied storage 持久化收口
- `fileaccess/transaction/mutation/FileAccessStorageReferenceMutationService`
  - 源对象引用计数递减与错误码收口
- `AccessLevelChangeTransactionHelper`
  - 保留原 helper 入口，作为 transaction façade

### 文件管理

- `FileManagementService`
  - 仅作为 façade，对外保留文件列表、详情、删除和统计入口
- `filemanagement/validator/FileManagementAdminValidator`
  - 管理员身份校验
- `filemanagement/query/FileManagementQueryService`
  - 文件列表查询
  - 文件详情查询
  - 组合记录查询与统计查询
- `filemanagement/query/FileManagementRecordQueryService`
  - 文件列表分页查询
  - 文件详情查询
- `filemanagement/query/FileManagementStatisticsQueryService`
  - 全局存储统计
  - 租户维度存储统计
- `filemanagement/command/FileAdminDeleteCommandService`
  - 管理员单文件删除
  - 存储对象引用判断
  - 删除审计
- `filemanagement/command/FileAdminBatchDeleteCommandService`
  - 管理员批量删除
  - 单文件删除编排
  - 批量结果与审计
- `filemanagement/deletion/FileManagementDeletionService`
  - 最后引用对象判定
  - 对象存储删除
  - 管理员删除事务提交
  - URL 缓存清理
- `filemanagement/audit/FileManagementAuditService`
  - 单文件删除审计
  - 批量删除审计

### 文件类型校验

- `FileTypeValidator`
  - 仅作为 façade，对外保留原有扩展名、Content-Type、大小、魔数校验入口
- `filetypevalidation/config/FileTypeRuleConfigService`
  - `FileTypeProperties` 访问
  - 允许范围与魔数检测开关读取
- `filetypevalidation/parser/FileTypeInputNormalizer`
  - 扩展名提取
  - Content-Type 归一化
- `filetypevalidation/policy/FileTypePolicyService`
  - 扩展名校验
  - Content-Type 校验
  - 文件大小校验
  - 声明侧组合校验
- `filetypevalidation/signature/FileSignatureValidationService`
  - 魔数检测开关判断
  - 文件头识别
  - 声明类型与检测类型匹配校验
- `filetypevalidation/signature/FileTypeSignatureInspector`
  - 文件头识别
  - 声明类型与检测类型匹配校验

### 秒传

- `InstantUploadService`
  - 仅作为 façade，对外保留秒传检查入口
- `instantupload/command/InstantUploadCheckCommandService`
  - 用户已有文件短路命中
  - 共享对象复用
  - 新 `FileRecord` 写入与引用计数增加
- `instantupload/query/InstantUploadRecordQueryService`
  - 用户已有文件查询
  - bucket 范围内共享对象查询
- `instantupload/persistence/InstantUploadPersistenceService`
  - 共享对象引用计数增加
  - 新 `FileRecord` 持久化
- `instantupload/factory/InstantUploadObjectFactory`
  - 秒传 `FileRecord` 构造
- `instantupload/storage/InstantUploadStorageService`
  - 已有文件 URL 解析
  - 共享对象公开 URL 生成
- `instantupload/assembler/InstantUploadResponseAssembler`
  - 秒传成功/需上传响应装配

### 删除事务

- `FileDeleteTransactionHelper`
  - 保留原 helper 入口，作为删除事务 façade
- `filedeletion/query/FileDeletionStorageObjectQueryService`
  - 存储对象最后引用查询收口
- `filedeletion/query/StorageObjectLastReferenceQueryService`
  - 查询是否为最后引用
- `filedeletion/transaction/UserFileDeleteTransactionService`
  - 用户删除文件短事务
  - 软删除记录
  - 递减租户用量与对象引用
- `filedeletion/transaction/AdminFileDeleteTransactionService`
  - 管理员删除文件短事务
  - 硬删除记录
  - 递减租户用量与对象引用
- `filedeletion/mutation/FileDeletionRecordMutationService`
  - 文件软删除 / 硬删除
  - 删除失败错误码收口
- `filedeletion/accounting/FileDeletionUsageAccountingService`
  - 租户用量递减收口
- `filedeletion/mutation/FileDeletionStorageReleaseService`
  - 对象引用递减
  - 引用归零后的 `StorageObject` 删除收口

### 审计

- `AuditLogService`
  - 保留统一审计记录入口，作为 audit façade
- `audit/command/AuditLogRecordCommandService`
  - 审计持久化执行
  - 吞异常并记录错误日志
- `audit/persistence/AuditLogPersistenceService`
  - `AuditLogRepository` 持久化收口

### 租户管理

- `TenantManagementService`
  - 仅作为 façade，对外保留租户生命周期和查询入口
- `tenantmanagement/command/TenantCreateCommandService`
  - 创建租户
  - 初始化租户使用量
  - 创建审计
- `tenantmanagement/command/TenantUpdateCommandService`
  - 配额与基础信息更新
  - 变更集审计
- `tenantmanagement/command/TenantStatusCommandService`
  - 租户状态切换
  - 状态审计
- `tenantmanagement/command/TenantDeleteCommandService`
  - 租户软删除
  - 删除审计
- `tenantmanagement/query/TenantRecordQueryService`
  - 租户查询
  - 租户使用量查询
  - 租户列表查询
- `tenantmanagement/query/TenantDetailQueryService`
  - 租户详情与使用量聚合
- `tenantmanagement/query/TenantListQueryService`
  - 租户列表查询
- `tenantmanagement/factory/TenantManagementObjectFactory`
  - `Tenant` / `TenantUsage` 构造
  - 租户详情 DTO 构造
- `tenantmanagement/mutation/TenantMutationService`
  - 配额更新校验
  - 状态流转
  - 软删除标记
- `tenantmanagement/persistence/TenantPersistenceService`
  - 租户保存
  - 租户使用量保存
- `tenantmanagement/audit/TenantManagementAuditService`
  - 创建、更新、状态变更、删除审计

### 上传事务

- `UploadPartTransactionHelper`
  - 保留保存分片记录入口，作为 uploadpart transaction façade
- `uploadpart/transaction/UploadPartSaveTransactionService`
  - 分片上传记录写入短事务
- `uploadpart/factory/UploadPartFactory`
  - `UploadPart` 创建
  - 上传时间与主键初始化
- `uploadpart/persistence/UploadPartPersistenceService`
  - `UploadPartRepository` 写入收口
- `uploadpart/query/UploadPartStateQueryService`
  - 已完成分片数量查询
  - 已上传分片查询
  - 已完成分片编号查询
- `uploadpart/query/UploadPartCompletionQueryService`
  - 持久化分片装配为 `CompletedPart`
  - multipart complete authoritative parts 读取收口
- `uploadpart/command/UploadPartSyncCommandService`
  - 分片状态全量同步到数据库
  - direct / multipart complete 统一走同步入口
- `uploadpart/query/UploadPartRecordQueryService`
  - `UploadPartRepository` 查询收口
  - `UploadPartMapper` 持久化记录读取收口
- `uploadpart/assembler/UploadCompletedPartAssembler`
  - `UploadPartPO` 到 `CompletedPart` 的排序与装配收口
- `UploadTransactionHelper`
  - 保留上传元数据落库入口，作为 uploadtx transaction façade
- `uploadtx/transaction/NewUploadTransactionService`
  - 新上传对象与文件记录写入
  - 租户用量递增
- `uploadtx/transaction/InstantUploadTransactionService`
  - 秒传场景下对象复用与文件记录写入
  - 租户用量递增
- `uploadtx/transaction/CompletedUploadTransactionService`
  - 完成上传后的任务状态更新
  - `StorageObject` / `FileRecord` 写入
- `uploadtx/transaction/CompletedInstantUploadTransactionService`
  - 完成秒传任务后的任务状态更新
  - 对象复用与文件记录写入
- `uploadtx/persistence/UploadMetadataPersistenceService`
  - `StorageObject` / `FileRecord` 持久化收口
- `uploadtx/accounting/UploadTenantUsageAccountingService`
  - 租户用量递增收口
- `uploadtx/mutation/UploadStorageReferenceMutationService`
  - 共享对象引用计数递增收口
- `uploadtx/mutation/UploadTaskStatusMutationService`
  - 上传任务完成态更新收口

### 上传任务

- `uploadtask/query/UploadTaskQueryService`
  - 上传任务按 ID 查询
  - 按用户 + 文件哈希查询续传任务
  - 用户任务列表查询
- `uploadtask/command/UploadTaskCommandService`
  - 新建上传中任务
  - 任务过期 / 中止状态更新
- `uploadtask/factory/UploadTaskFactory`
  - `UploadTask` 创建与默认字段装配
  - 过期时间与上传中状态初始化

## 重构收益

- controller 不需要感知内部拆分，接口层回归风险低
- 用例边界更清晰，后续排查能直接定位到 init/complete/query
- 单测可以从 façade 委托测试下沉到用例级测试，不再把所有行为都压在一个类上
- 更接近 `zhicore-content` 的 `Facade + CommandService` 风格
- direct / multipart 不再各自直连 `UploadTaskRepository` 处理任务查找、创建和状态流转
- direct / multipart 也不再自己直连 `UploadPartMapper` 组装完成态分片
- 上传主链路中的对象工厂、记录查询、元数据持久化和状态变更边界更明确

## 当前仍偏重的类

按代码规模和职责密度看，后续优先级建议如下：

1. `AccessLevelChangeTransactionHelper`
   - 已完成 façade + transaction + mutation/persistence 收敛
2. `FileDeleteTransactionHelper`
   - 已完成 façade + query/mutation/accounting 收敛
3. `AuditLogService`
   - 已完成 façade + recorder + persistence 收敛，后续可继续下沉 details factory
4. `UploadPartTransactionHelper`
   - 已完成 façade + transaction service + factory/query/persistence 收敛
5. `UploadTransactionHelper`
   - 已完成 façade + transaction service + persistence/accounting/mutation 收敛

## 后续建议

当前应用层主要重构已基本完成，下一阶段如果继续收敛，建议转向更细粒度的 repository / factory / assembler 边界，原因：

- 目前应用层 façade、命令、查询基本已经收敛
- 上传事务 façade 也已经和 direct/multipart 链路保持一致
- 上传任务查询 / 创建 / 状态流转也已经通过 query/command/factory 从 direct/multipart 用例中抽离
- 分片状态查询 / 同步 / 完成态装配也已经通过 query/assembler/factory/persistence 从 direct/multipart 用例中抽离
- 文件访问事务、删除事务和审计入口也已经完成 mutation / persistence / accounting 收敛
- 当前应用层已无遗留 `*Support.java`

建议延续同一模式：

- 继续把偏重的 façade 内对象构造与 repository 编排解耦
- 审计 details factory 与异常码装配进一步下沉
- 分片进度、锁协同、对象存储 authoritative state 继续细分独立用例
- direct / multipart 间可复用的完成态校验逻辑进一步归并

这样上传、访问、管理、租户、校验、秒传，以及访问级别/删除事务编排和审计入口都已经统一，后续 controller、缓存、审计与异常码排查会更自然。
