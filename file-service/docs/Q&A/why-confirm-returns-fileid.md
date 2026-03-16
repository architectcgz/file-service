# Q&A: 为什么调用 `complete` 能获取到 `fileId`？

## 问题

为什么前端通过预签名 URL 直传 MinIO 之后，调用 `POST /api/v1/upload-sessions/{id}/complete` 就能获取到 `fileId`？

## 回答

因为当前实现里，`fileId` 不是在创建上传会话阶段生成的，而是在 `complete` 阶段创建 `FileRecord` 时生成的。

直传上传链路分成两段：

### 1. 创建会话阶段只负责生成上传凭证

`POST /api/v1/upload-sessions` 且 `uploadMode=PRESIGNED_SINGLE` 的作用是：

- 校验文件名、类型、大小、哈希
- 创建 `UploadSession`
- 生成可直接上传到 MinIO 的 `singleUploadUrl`

这一阶段只是在告诉前端：

- 文件应该传到哪里
- 用什么方法上传
- 这个上传 URL 什么时候过期

它不会创建正式业务记录，所以也不会返回 `fileId`。

这里返回的是：

- `uploadSessionId`
- `singleUploadUrl`
- `singleUploadMethod`
- `singleUploadHeaders`
- `singleUploadExpiresInSeconds`

### 2. `complete` 阶段才会正式创建文件记录

当前端已经把文件传到 MinIO 之后，再调用 `POST /api/v1/upload-sessions/{id}/complete`。

这一阶段服务端会做几件事：

1. 根据 upload session 内的对象路径去对象存储执行 `HeadObject`
2. 确认对象确实存在
3. 读取真实的文件大小和 `Content-Type`
4. 判断是否命中去重逻辑
5. 创建或复用 `StorageObject`
6. 创建 `FileRecord`
7. 生成 `fileId`
8. 返回 `fileId`

也就是说，`complete` 能返回 `fileId`，本质上是因为：

- 这一步才真正把“一个已经上传成功的对象”变成“系统内可引用的文件记录”

## 为什么不在创建会话阶段就返回 `fileId`？

因为在创建上传会话阶段，文件还不一定真的上传成功。

如果在创建会话阶段就生成 `fileId`，会有几个问题：

- 前端可能拿到 `fileId` 后没有真正上传文件
- 上传可能中途失败
- 上传到 MinIO 的对象可能不存在
- 业务系统可能提前保存了一个实际上不可用的 `fileId`

这样会产生很多“有记录、没文件”或者“有 `fileId`、没落库确认”的脏数据。

所以当前实现把正式建档放在 `complete` 阶段，这样更合理：

- 创建会话只负责给上传通道
- `complete` 才负责正式入库

## `complete` 请求依赖什么信息？

`complete` 需要前端至少带回：

- `uploadSessionId`
- `contentType`

其中：

- `uploadSessionId` 来自创建会话响应
- `contentType` 用于兜底补齐对象元数据里的内容类型

## 一句话总结

`complete` 之所以能拿到 `fileId`，是因为当前系统把 `fileId` 的生成时机放在了“确认对象已成功上传并正式创建 FileRecord”的这一步，而不是放在“创建上传会话”的阶段。
