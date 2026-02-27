# 核心业务流程

## 1. 基础上传流程

```
客户端 → UploadController → UploadApplicationService
                                ├── FileTypeValidator（类型/大小校验）
                                ├── ImageProcessor（图片压缩/WebP/缩略图，仅图片）
                                ├── StorageObjectRepository（哈希去重检查）
                                │   ├── 命中 → 引用计数 +1，跳过存储
                                │   └── 未命中 → S3StorageService.upload()
                                ├── FileRecordRepository.save()
                                └── AuditLogService.log()
```

关键点：
- 上传前先校验文件类型和大小
- 图片上传额外执行压缩、WebP 转换、缩略图生成
- 基于文件哈希做存储层去重，相同文件只存一份
- 整个流程在 `@Transactional` 事务内

## 2. 分片上传流程

```
┌─ 初始化 ─────────────────────────────────────────────┐
│ POST /multipart/init                                  │
│   → 检查是否存在可续传的任务（同 fileHash + UPLOADING）│
│   → 存在 → 返回已有 taskId + 已完成分片列表           │
│   → 不存在 → S3.createMultipartUpload()               │
│            → 创建 UploadTask（status=UPLOADING）       │
└───────────────────────────────────────────────────────┘
         │
┌─ 上传分片 ────────────────────────────────────────────┐
│ PUT /multipart/{taskId}/parts/{partNumber}             │
│   → 幂等检查（同 partNumber 已上传则跳过）             │
│   → S3.uploadPart()                                    │
│   → 保存 UploadPart（partNumber, etag, size）          │
└───────────────────────────────────────────────────────┘
         │
┌─ 完成上传 ────────────────────────────────────────────┐
│ POST /multipart/{taskId}/complete                      │
│   → 校验所有分片已上传                                 │
│   → S3.completeMultipartUpload()                       │
│   → 创建 StorageObject + FileRecord                    │
│   → UploadTask.status = COMPLETED                      │
└───────────────────────────────────────────────────────┘
```

## 3. 客户端直传流程

```
┌─ 初始化 ──────────────────────────────────────────────┐
│ POST /direct-upload/init                               │
│   → 秒传检查（fileHash 匹配已有 StorageObject）        │
│   → 断点续传检查（同 fileHash + UPLOADING 任务）        │
│   → 均未命中 → S3.createMultipartUpload()              │
│              → 创建 UploadTask                          │
└────────────────────────────────────────────────────────┘
         │
┌─ 获取分片预签名 URL ─────────────────────────────────┐
│ POST /direct-upload/part-urls                          │
│   → 为每个分片生成 S3 预签名 PUT URL                   │
│   → 客户端直接上传到 S3，不经过服务端                   │
└───────────────────────────────────────────────────────┘
         │
┌─ 完成直传 ────────────────────────────────────────────┐
│ POST /direct-upload/{taskId}/complete                   │
│   → S3.completeMultipartUpload()                       │
│   → 创建 StorageObject + FileRecord                    │
│   → UploadTask.status = COMPLETED                      │
└───────────────────────────────────────────────────────┘
```

优势：大文件上传不经过服务端，减轻带宽和 CPU 压力。

## 4. 秒传流程

```
客户端计算文件哈希 → POST /instant-upload/check
   → InstantUploadService.check(appId, fileHash)
   → StorageObjectRepository.findByHash(appId, fileHash, algorithm)
      ├── 命中 → 引用计数 +1
      │        → 创建新 FileRecord 指向同一 StorageObject
      │        → 返回 { instantUpload: true, fileRecord }
      └── 未命中 → 返回 { instantUpload: false }
```

关键点：
- 秒传基于 appId + fileHash + hashAlgorithm 三元组唯一匹配
- 支持跨用户秒传（同一 appId 下不同用户上传相同文件）
- StorageObject 通过引用计数管理生命周期

## 5. 预签名 URL 上传流程

```
POST /upload/presign
   → PresignedUrlService.getPresignedUploadUrl()
   → 生成存储路径
   → S3.presignPutObject(path, expiration=900s)
   → 返回 { presignedUrl, storagePath }

客户端直接 PUT 文件到 presignedUrl

POST /upload/confirm
   → PresignedUrlService.confirmUpload()
   → S3.headObject() 验证文件已上传
   → 创建 StorageObject + FileRecord
```

## 6. 文件访问控制

```
GET /files/{fileId}/url
   → FileAccessService.getFileUrl()
   → 权限验证（公开文件任何人可访问，私有文件仅所有者）
   → 根据 AccessLevel 返回不同 URL：
      ├── PUBLIC → CDN 域名永久 URL（缓存到 Redis，TTL 3600s）
      └── PRIVATE → S3 预签名 URL（有效期 3600s，不缓存）
```

## 7. 文件删除流程

```
DELETE /upload/{fileRecordId}
   → UploadApplicationService.deleteFile()
   → 权限验证（仅所有者可删除）
   → FileRecord.markAsDeleted()（软删除）
   → StorageObject.decrementReferenceCount()
      ├── 引用计数 > 0 → 仅删除 FileRecord
      └── 引用计数 = 0 → S3.deleteObject() 删除实际文件
   → 清除 Redis 缓存
   → AuditLogService.log()
```

## 8. 过期任务清理

```
UploadTaskCleanupScheduler（cron: 每小时执行）
   → 查询 status=UPLOADING 且 expiresAt < now 的任务
   → 对每个过期任务：
      → S3.abortMultipartUpload()
      → UploadTask.markExpired()
      → 清理关联的 UploadPart 记录
```
