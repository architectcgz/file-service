# Requirements Document

## Introduction

为博客系统的上传服务实现 S3 兼容存储支持，使文件能够上传到 RustFS/MinIO 对象存储而不是本地文件系统。这将提供更好的可扩展性、持久性和分布式部署能力。

本需求还包括：
1. 文件元数据存储表设计，用于记录上传文件的信息
2. 大文件断点续传功能，支持分片上传和断点恢复

RustFS 是一个高性能、分布式的对象存储解决方案，100% 兼容 S3 协议，使用 Apache 2.0 许可证开源。它可以使用标准的 AWS S3 SDK 进行访问。

## Glossary

- **Upload_Service**: 博客系统的文件上传服务 (blog-upload)
- **Storage_Service**: 存储服务接口，定义文件上传、删除、获取URL等操作
- **S3_Storage_Service**: 实现 S3 协议的存储服务，支持 RustFS、MinIO、AWS S3、阿里云 OSS 等
- **RustFS**: 高性能分布式对象存储，100% 兼容 S3 API，使用 Rust 编写
- **Bucket**: S3 存储桶，用于组织和存储对象
- **CDN_Domain**: 内容分发网络域名，用于加速文件访问
- **File_Record**: 文件记录实体，存储文件元数据信息
- **Multipart_Upload**: S3 分片上传，用于大文件断点续传
- **Upload_Part**: 分片上传的单个分片
- **Upload_Task**: 上传任务，记录分片上传的状态和进度

## Requirements

### Requirement 1: S3 存储服务实现

**User Story:** As a developer, I want to upload files to MinIO/S3 storage, so that files are stored in a scalable and distributed object storage system.

#### Acceptance Criteria

1. WHEN storage.type is set to "s3", THE Upload_Service SHALL use S3_Storage_Service for file operations
2. WHEN storage.type is set to "local" or not specified, THE Upload_Service SHALL use LocalStorageService (existing behavior)
3. THE S3_Storage_Service SHALL implement the StorageService interface with upload, delete, getUrl, and exists methods
4. WHEN uploading a file, THE S3_Storage_Service SHALL store the file in the configured S3 bucket with the specified path
5. WHEN deleting a file, THE S3_Storage_Service SHALL remove the object from the S3 bucket
6. WHEN getting a file URL, THE S3_Storage_Service SHALL return the CDN domain URL if configured, otherwise return the S3 endpoint URL

### Requirement 2: S3 配置管理

**User Story:** As a system administrator, I want to configure S3 connection parameters, so that I can connect to different S3-compatible storage services.

#### Acceptance Criteria

1. THE Upload_Service SHALL support configuration of S3 endpoint URL via environment variable or configuration file
2. THE Upload_Service SHALL support configuration of S3 access key and secret key
3. THE Upload_Service SHALL support configuration of S3 bucket name
4. THE Upload_Service SHALL support configuration of S3 region
5. THE Upload_Service SHALL support optional CDN domain configuration for public file access
6. WHEN S3 configuration is invalid, THE Upload_Service SHALL fail fast at startup with a clear error message

### Requirement 3: Bucket 自动创建

**User Story:** As a developer, I want the bucket to be automatically created if it doesn't exist, so that I don't need to manually create it.

#### Acceptance Criteria

1. WHEN the S3_Storage_Service initializes, THE service SHALL check if the configured bucket exists
2. IF the bucket does not exist, THEN THE S3_Storage_Service SHALL create the bucket automatically
3. WHEN bucket creation fails, THE S3_Storage_Service SHALL log the error and throw an exception

### Requirement 4: 错误处理

**User Story:** As a developer, I want proper error handling for S3 operations, so that I can diagnose and fix issues.

#### Acceptance Criteria

1. WHEN an S3 upload fails, THE S3_Storage_Service SHALL throw a BusinessException with a descriptive message
2. WHEN an S3 delete fails, THE S3_Storage_Service SHALL log the error and throw a BusinessException
3. WHEN S3 connection fails, THE S3_Storage_Service SHALL throw an exception with connection details
4. IF a file does not exist when checking, THEN THE S3_Storage_Service SHALL return false without throwing an exception

### Requirement 5: Docker 基础设施

**User Story:** As a developer, I want to use the existing RustFS/MinIO service in Docker infrastructure, so that I can easily run the complete system locally.

#### Acceptance Criteria

1. THE docker-compose configuration SHALL include a RustFS/MinIO compatible service (already exists as `minio` service)
2. THE RustFS service SHALL expose the API port (9000) and console port (9001)
3. THE RustFS service SHALL use persistent volume for data storage
4. THE docker-compose configuration SHALL include default credentials for development (admin/admin123456)

### Requirement 6: 文件元数据存储

**User Story:** As a developer, I want to store file metadata in database, so that I can track uploaded files, manage file lifecycle, and support file queries.

#### Acceptance Criteria

1. THE Upload_Service SHALL store file metadata in a `file_records` table
2. THE File_Record SHALL include: id, user_id, original_name, storage_path, file_size, content_type, file_hash (MD5/SHA256), status, created_at, updated_at
3. WHEN a file is uploaded successfully, THE Upload_Service SHALL create a File_Record with status "completed"
4. WHEN a file is deleted, THE Upload_Service SHALL update the File_Record status to "deleted" (soft delete)
5. THE Upload_Service SHALL support querying files by user_id, content_type, and status
6. THE Upload_Service SHALL prevent duplicate uploads by checking file_hash for the same user

### Requirement 7: 大文件分片上传

**User Story:** As a user, I want to upload large files in chunks, so that I can resume interrupted uploads without starting over.

#### Acceptance Criteria

1. WHEN file size exceeds the configured threshold (default 10MB), THE Upload_Service SHALL use multipart upload
2. THE Upload_Service SHALL split large files into configurable chunk sizes (default 5MB per chunk)
3. THE Upload_Service SHALL store upload task information in `upload_tasks` table
4. THE Upload_Task SHALL include: id, user_id, file_name, file_size, file_hash, upload_id (S3 multipart upload ID), total_parts, status, created_at, expires_at
5. THE Upload_Service SHALL store uploaded parts information in `upload_parts` table
6. THE Upload_Part SHALL include: id, task_id, part_number, etag, size, uploaded_at

### Requirement 8: 断点续传

**User Story:** As a user, I want to resume an interrupted upload from where it stopped, so that I don't waste bandwidth re-uploading completed parts.

#### Acceptance Criteria

1. WHEN initiating an upload, THE Upload_Service SHALL check for existing incomplete Upload_Task with matching file_hash
2. IF an incomplete task exists, THEN THE Upload_Service SHALL return the task_id and list of completed parts
3. THE client SHALL only upload missing parts based on the completed parts list
4. WHEN all parts are uploaded, THE Upload_Service SHALL complete the multipart upload and create a File_Record
5. THE Upload_Service SHALL automatically clean up expired incomplete upload tasks (default 24 hours)
6. WHEN an upload is cancelled, THE Upload_Service SHALL abort the S3 multipart upload and clean up task records

### Requirement 9: 上传进度查询

**User Story:** As a user, I want to check the progress of my upload, so that I know how much has been uploaded.

#### Acceptance Criteria

1. THE Upload_Service SHALL provide an API to query upload task progress by task_id
2. THE progress response SHALL include: total_parts, completed_parts, uploaded_bytes, total_bytes, percentage
3. WHEN the task does not exist or is expired, THE Upload_Service SHALL return a 404 error
4. THE Upload_Service SHALL support listing all active upload tasks for a user

### Requirement 10: 秒传功能

**User Story:** As a user, I want to instantly complete upload if the same file already exists, so that I don't waste time and bandwidth re-uploading.

#### Acceptance Criteria

1. WHEN initiating an upload, THE Upload_Service SHALL check if a completed file with the same file_hash exists for the user
2. IF a completed file exists, THEN THE Upload_Service SHALL return the existing file's URL immediately (instant upload)
3. THE instant upload response SHALL indicate that this was an instant upload (not a new upload)
4. THE Upload_Service SHALL NOT create duplicate file records for the same user and file_hash

### Requirement 11: 文件去重与引用计数

**User Story:** As a system administrator, I want to avoid storing duplicate files, so that storage space is used efficiently.

#### Acceptance Criteria

1. THE Upload_Service SHALL use file_hash to identify duplicate files across all users
2. THE file_records table SHALL include a reference_count field to track how many users reference the same storage object
3. WHEN a new user uploads a file with an existing hash, THE Upload_Service SHALL increment the reference_count instead of storing a duplicate
4. WHEN a file is deleted, THE Upload_Service SHALL decrement the reference_count
5. WHEN reference_count reaches 0, THE Upload_Service SHALL delete the actual S3 object
6. THE Upload_Service SHALL maintain a separate storage_objects table to track unique files

### Requirement 12: 预签名 URL 直传

**User Story:** As a user, I want to upload files directly to S3, so that uploads are faster and don't burden the application server.

#### Acceptance Criteria

1. THE Upload_Service SHALL support generating presigned URLs for direct S3 upload
2. THE presigned URL SHALL have a configurable expiration time (default 15 minutes)
3. WHEN requesting a presigned URL, THE client SHALL provide file metadata (name, size, content_type, hash)
4. AFTER direct upload completes, THE client SHALL notify the Upload_Service to create the file record
5. THE Upload_Service SHALL verify the uploaded file exists in S3 before creating the record
6. THE Upload_Service SHALL support presigned URLs for multipart upload parts

### Requirement 13: 文件类型限制

**User Story:** As a system administrator, I want to restrict uploadable file types, so that only allowed content is stored.

#### Acceptance Criteria

1. THE Upload_Service SHALL support configuring allowed MIME types
2. THE Upload_Service SHALL support configuring allowed file extensions
3. WHEN a file with disallowed type is uploaded, THE Upload_Service SHALL reject with a 400 error
4. THE Upload_Service SHALL validate both Content-Type header and file extension
5. THE default allowed types SHALL include common image, video, and document formats

### Requirement 14: 文件访问权限控制

**User Story:** As a user, I want to control who can access my uploaded files, so that private files remain private.

#### Acceptance Criteria

1. THE file_records table SHALL include an access_level field (public, private)
2. WHEN access_level is public, THE Upload_Service SHALL return a permanent public URL
3. WHEN access_level is private, THE Upload_Service SHALL generate a temporary presigned URL for access
4. THE temporary URL SHALL have a configurable expiration time (default 1 hour)
5. THE Upload_Service SHALL verify user ownership before generating private file URLs
6. THE default access_level SHALL be public
