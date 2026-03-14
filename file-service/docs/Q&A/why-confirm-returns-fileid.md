# Q&A: 为什么调用 `confirm` 能获取到 `fileId`？

## 问题

为什么前端通过预签名 URL 直传 MinIO 之后，调用 `confirm` 就能获取到 `fileId`？

## 回答

因为当前实现里，`fileId` 不是在 `presign` 阶段生成的，而是在 `confirm` 阶段创建 `FileRecord` 时生成的。

直传上传链路分成两段：

### 1. `presign` 阶段只负责生成上传凭证

`POST /api/v1/upload/presign` 的作用是：

- 校验文件名、类型、大小
- 生成 `storagePath`
- 生成可直接上传到 MinIO 的 `presignedUrl`

这一阶段只是在告诉前端：

- 文件应该传到哪里
- 用什么方法上传
- 这个上传 URL 什么时候过期

它不会创建正式业务记录，所以也不会返回 `fileId`。

对应实现见：

- [PresignedUrlService.java](/home/azhi/workspace/projects/file-service/file-service/src/main/java/com/architectcgz/file/application/service/PresignedUrlService.java#L114)

这里返回的是：

- `presignedUrl`
- `storagePath`
- `expiresAt`
- `method`
- `headers`

### 2. `confirm` 阶段才会正式创建文件记录

当前端已经把文件传到 MinIO 之后，再调用 `POST /api/v1/upload/confirm`。

这一阶段服务端会做几件事：

1. 根据 `storagePath` 去对象存储执行 `HeadObject`
2. 确认对象确实存在
3. 读取真实的文件大小和 `Content-Type`
4. 判断是否命中去重逻辑
5. 创建或复用 `StorageObject`
6. 创建 `FileRecord`
7. 生成 `fileId`
8. 返回 `fileId` 和 `url`

关键代码在这里：

- 读取对象元数据：[PresignedUrlService.java](/home/azhi/workspace/projects/file-service/file-service/src/main/java/com/architectcgz/file/application/service/PresignedUrlService.java#L138)
- 创建 `FileRecord` 并生成 `fileId`：[PresignedUrlService.java](/home/azhi/workspace/projects/file-service/file-service/src/main/java/com/architectcgz/file/application/service/PresignedUrlService.java#L195)
- 返回结果中的 `fileId`：[PresignedUrlService.java](/home/azhi/workspace/projects/file-service/file-service/src/main/java/com/architectcgz/file/application/service/PresignedUrlService.java#L219)

也就是说，`confirm` 能返回 `fileId`，本质上是因为：

- 这一步才真正把“一个已经上传成功的对象”变成“系统内可引用的文件记录”

## 为什么不在 `presign` 阶段就返回 `fileId`？

因为在 `presign` 阶段，文件还不一定真的上传成功。

如果在 `presign` 阶段就生成 `fileId`，会有几个问题：

- 前端可能拿到 `fileId` 后没有真正上传文件
- 上传可能中途失败
- 上传到 MinIO 的对象可能不存在
- 业务系统可能提前保存了一个实际上不可用的 `fileId`

这样会产生很多“有记录、没文件”或者“有 `fileId`、没落库确认”的脏数据。

所以当前实现把正式建档放在 `confirm` 阶段，这样更合理：

- `presign` 只负责给上传通道
- `confirm` 才负责正式入库

## `confirm` 请求依赖什么信息？

`confirm` 需要前端把这些信息带回来：

- `storagePath`
- `fileHash`
- `originalFilename`
- `accessLevel`

其中：

- `storagePath` 来自 `presign` 响应
- `fileHash` 和原始文件有关
- `originalFilename` 用于生成文件记录展示信息

请求结构见：

- [ConfirmUploadRequest.java](/home/azhi/workspace/projects/file-service/file-service/src/main/java/com/architectcgz/file/application/dto/ConfirmUploadRequest.java#L1)

## 一句话总结

`confirm` 之所以能拿到 `fileId`，是因为当前系统把 `fileId` 的生成时机放在了“确认对象已成功上传并正式创建 FileRecord”的这一步，而不是放在“申请上传凭证”的 `presign` 阶段。
