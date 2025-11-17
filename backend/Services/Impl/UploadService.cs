using FileService.Constants;
using FileService.Exceptions;
using FileService.Services.Interfaces;
using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Repositories.Entities;
using Microsoft.EntityFrameworkCore;
using System.Security.Cryptography;
using Microsoft.Extensions.Options;
using UploadStatus = FileService.Repositories.Entities.UploadStatus;

namespace FileService.Services.Impl;

public class UploadService(
    IStorageService storageService,
    ILogger<UploadService> logger, 
    IOptions<Config.RustFSConfig> rustFSConfigOptions,
    FileServiceDbContext dbContext) : IUploadService
{
    private readonly Config.RustFSConfig _rustFSConfig = rustFSConfigOptions.Value;
    
    /// <summary>
    /// 计算文件的SHA256哈希值
    /// </summary>
    /// <param name="stream">文件流</param>
    /// <returns>SHA256哈希值（十六进制字符串）</returns>
    private static async Task<string> ComputeFileHashAsync(Stream stream)
    {
        using var sha256 = SHA256.Create();
        var hashBytes = await sha256.ComputeHashAsync(stream);
        stream.Position = 0; // 重置流位置以便后续使用
        return BitConverter.ToString(hashBytes).Replace("-", "").ToLowerInvariant();
    }

    /// <summary>
    /// 上传文件到 RustFS（支持去重和并发控制）
    /// </summary>
    /// <param name="file">前端上传的文件</param>
    /// <param name="bucket">目标存储桶（必需）</param>
    /// <param name="uploaderId">上传者用户ID（可选）</param>
    /// <param name="folder">目标文件夹（可选，默认根据文件类型自动判断）</param>
    /// <returns>上传成功返回文件的 URL，失败返回 null</returns>
    public async Task<string> UploadFileAsync(IFormFile file, string bucket, string? uploaderId = null, string? folder = null)
    {
        if (file.Length == 0)
        {
            throw new BusinessException(BusinessError.FileIsNull);
        }

        try
        {
            // 1. 计算文件哈希值（用于去重）
            using var memoryStream = new MemoryStream();
            await file.CopyToAsync(memoryStream);
            memoryStream.Position = 0;
            
            var fileHash = await ComputeFileHashAsync(memoryStream);
            logger.LogInformation($"文件哈希值: {fileHash}, 文件名: {file.FileName}, 大小: {file.Length} 字节");
            
            // 2. 检查数据库中是否已存在相同哈希的文件（支持分表）
            var existingFile = await dbContext.GetUploadedFilesByHash(fileHash)
                .AsTracking() // 需要修改实体，必须跟踪
                .FirstOrDefaultAsync(f => f.FileHash == fileHash && !f.Deleted);
            
            if (existingFile != null)
            {
                // 检查上传状态
                if (existingFile.UploadStatus == (int)UploadStatus.Success)
                {
                    // 文件已成功上传，更新引用计数
                    existingFile.ReferenceCount++;
                    existingFile.LastAccessTime = DateTimeOffset.UtcNow;
                    await dbContext.SaveChangesAsync();
                    
                    logger.LogInformation($"文件已存在（去重），哈希: {fileHash}, 引用计数: {existingFile.ReferenceCount}, 返回URL: {existingFile.FileUrl}");
                    return existingFile.FileUrl;
                }
                else if (existingFile.UploadStatus == (int)UploadStatus.Failed)
                {
                    // 上次上传失败，删除旧记录，重新上传
                    logger.LogWarning($"检测到上次上传失败的记录，删除并重试: {fileHash}");
                    dbContext.UploadedFiles.Remove(existingFile);
                    await dbContext.SaveChangesAsync();
                    // 继续执行后续上传逻辑
                }
                else if (existingFile.UploadStatus == (int)UploadStatus.Uploading)
                {
                    // 正在上传中（可能是并发请求或上次未完成）
                    var uploadingDuration = DateTimeOffset.UtcNow - existingFile.CreateTime;
                    if (uploadingDuration.TotalMinutes > 5)
                    {
                        // 超过5分钟仍在上传中，认为已失败，删除并重试
                        logger.LogWarning($"检测到超时的上传记录，删除并重试: {fileHash}");
                        dbContext.UploadedFiles.Remove(existingFile);
                        await dbContext.SaveChangesAsync();
                    }
                    else
                    {
                        // 并发上传：等待第一个上传完成（最多等待30秒）
                        logger.LogInformation($"检测到并发上传，等待第一个上传完成: {fileHash}");
                        
                        var maxWaitSeconds = 30;
                        var pollIntervalMs = 500; // 每500ms轮询一次
                        var elapsedSeconds = 0;
                        
                        while (elapsedSeconds < maxWaitSeconds)
                        {
                            await Task.Delay(pollIntervalMs);
                            elapsedSeconds += pollIntervalMs / 1000;
                            
                            // 重新查询状态
                            var currentFile = await dbContext.GetUploadedFilesByHash(fileHash)
                                .AsNoTracking()
                                .FirstOrDefaultAsync(f => f.FileHash == fileHash && !f.Deleted);
                            
                            if (currentFile == null)
                            {
                                // 记录被删除了，可以重新上传
                                logger.LogInformation($"原上传记录已删除，允许重新上传: {fileHash}");
                                break;
                            }
                            else if (currentFile.UploadStatus == (int)UploadStatus.Success)
                            {
                                // 第一个上传成功了，直接返回
                                logger.LogInformation($"并发上传等待成功，第一个上传已完成: {fileHash}");
                                return currentFile.FileUrl;
                            }
                            else if (currentFile.UploadStatus == (int)UploadStatus.Failed)
                            {
                                // 第一个上传失败了，可以重新上传
                                logger.LogWarning($"第一个上传失败，允许重新上传: {fileHash}");
                                var fileToDelete = await dbContext.GetUploadedFilesByHash(fileHash)
                                    .FirstOrDefaultAsync(f => f.Id == currentFile.Id);
                                if (fileToDelete != null)
                                {
                                    dbContext.UploadedFiles.Remove(fileToDelete);
                                    await dbContext.SaveChangesAsync();
                                }
                                break;
                            }
                            // 否则继续等待
                        }
                        
                        // 超时仍在上传中
                        if (elapsedSeconds >= maxWaitSeconds)
                        {
                            throw new BusinessException(BusinessError.UploadError.Code, 
                                $"文件正在上传中，已等待{maxWaitSeconds}秒，请稍后重试");
                        }
                    }
                }
            }
            
            // 3. 文件不存在，准备上传
            // bucket参数已通过方法签名保证非空
            var targetBucket = bucket;
            
            // 验证bucket是否存在
            var bucketExists = await storageService.BucketExistsAsync(targetBucket);
            if (!bucketExists)
            {
                logger.LogWarning($"存储桶不存在 - Bucket: {targetBucket}");
                throw new BusinessException(BusinessError.UploadError.Code, $"存储桶 '{targetBucket}' 不存在，请先创建存储桶");
            }
            
            // 使用传入的folder参数，如果未提供则根据文件类型自动判断
            var targetFolder = string.IsNullOrWhiteSpace(folder) 
                ? GetDefaultFolderByFileType(file.ContentType) 
                : folder;
            
            // 生成带有子目录的文件名（使用哈希值的前16位+扩展名，更易识别）
            var fileExtension = Path.GetExtension(file.FileName);
            var fileName = string.IsNullOrEmpty(targetFolder) 
                ? $"{fileHash[..16]}_{Guid.NewGuid():N}{fileExtension}"
                : $"{targetFolder}/{fileHash[..16]}_{Guid.NewGuid():N}{fileExtension}";
            
            var proxyFileUrl = storageService.GetProxyUrlByFileType(fileName, file.ContentType, targetBucket);
            
            // 4. 先保存文件记录到数据库（支持分表）- 极简化版本
            var uploadedFile = new UploadedFile
            {
                Id = Guid.NewGuid(),
                FileHash = fileHash,
                FileKey = fileName,
                FileUrl = proxyFileUrl,
                BucketName = targetBucket,  // 记录 bucket 信息
                ReferenceCount = 1,
                UploaderId = uploaderId,
                CreateTime = DateTimeOffset.UtcNow,
                LastAccessTime = DateTimeOffset.UtcNow,
                UploadStatus = (int)UploadStatus.Uploading,
                Deleted = false
            };
            
            await dbContext.AddToShardTableAsync(uploadedFile);
            logger.LogInformation($"数据库记录创建成功（状态: Uploading），准备上传文件: {fileName}");
            
            // 5. 再上传到存储服务
            try
            {
                memoryStream.Position = 0;
                await storageService.UploadStreamAsync(memoryStream, fileName, targetBucket);
                
                // 上传成功，更新状态
                var uploadedFileToUpdate = await dbContext.GetUploadedFilesByHash(fileHash)
                    .FirstOrDefaultAsync(f => f.Id == uploadedFile.Id);
                if (uploadedFileToUpdate != null)
                {
                    uploadedFileToUpdate.UploadStatus = (int)UploadStatus.Success;
                    await dbContext.SaveChangesAsync();
                }
                
                logger.LogInformation($"文件上传成功，状态已更新: {fileName}");
            }
            catch (Exception uploadEx)
            {
                // RustFS 上传失败，标记为失败状态
                logger.LogError($"RustFS上传失败，标记为失败状态: {uploadEx.Message}");
                
                var fileToMarkFailed = await dbContext.GetUploadedFilesByHash(fileHash)
                    .FirstOrDefaultAsync(f => f.Id == uploadedFile.Id);
                if (fileToMarkFailed != null)
                {
                    fileToMarkFailed.UploadStatus = (int)UploadStatus.Failed;
                    await dbContext.SaveChangesAsync();
                    logger.LogInformation($"已标记为失败状态: {fileHash}");
                }
                
                throw new BusinessException(BusinessError.UploadError.Code, $"文件上传失败: {uploadEx.Message}");
            }
            
            logger.LogInformation($"上传成功，代理文件URL: {proxyFileUrl}, 文件记录ID: {uploadedFile.Id}");
            return proxyFileUrl;
        }
        catch (Exception ex)
        {
            logger.LogError($"上传异常: {ex.Message}");
            logger.LogError($"异常详情: {ex}");
            throw; // 重新抛出异常，让Controller能够获取到具体的错误信息
        }
    }

    public Task<string> GetUploadToken()
    {
        // RustFS不需要token，直接返回空字符串
        return Task.FromResult(string.Empty);
    }

    /// <summary>
    /// 生成文件直传签名（带去重检查）
    /// </summary>
    /// <param name="request">直传签名请求</param>
    /// <param name="fileCategory">文件分类（可选）</param>
    /// <param name="maxSizeBytes">最大文件大小限制</param>
    /// <returns>直传签名响应</returns>
    public async Task<DirectUploadSignatureResponseDto> GetDirectUploadSignatureAsync(DirectUploadSignatureRequestDto request, string? fileCategory = null, long maxSizeBytes = 10485760)
    {
        try
        {
            // 基本验证由 Data Annotations 自动处理，这里不需要重复验证
            
            // 验证bucket是否存在
            var bucketExists = await storageService.BucketExistsAsync(request.Bucket);
            if (!bucketExists)
            {
                logger.LogWarning($"存储桶不存在 - Bucket: {request.Bucket}");
                return new DirectUploadSignatureResponseDto
                {
                    Success = false,
                    Message = $"存储桶 '{request.Bucket}' 不存在，请先创建存储桶"
                };
            }
            
            //  去重检查：如果提供了文件哈希，先检查数据库中是否已存在（支持分表）
            if (!string.IsNullOrEmpty(request.FileHash))
            {
                logger.LogInformation($"直传去重检查 - 文件哈希: {request.FileHash}, 文件名: {request.FileName}");
                
                // 使用分表查询（根据哈希值）
                var existingFile = await dbContext.GetUploadedFilesByHash(request.FileHash)
                    .AsTracking()
                    .FirstOrDefaultAsync(f => f.FileHash == request.FileHash && !f.Deleted);
                
                if (existingFile != null)
                {
                    // 更新引用计数
                    existingFile.ReferenceCount++;
                    existingFile.LastAccessTime = DateTimeOffset.UtcNow;
                    await dbContext.SaveChangesAsync();
                    
                    logger.LogInformation($"直传去重命中 - 哈希: {request.FileHash}, 引用计数: {existingFile.ReferenceCount}, 返回URL: {existingFile.FileUrl}");

                    return new DirectUploadSignatureResponseDto
                    {
                        Success = true,
                        NeedUpload = false,
                        ExistingFileUrl = existingFile.FileUrl,
                        FileKey = existingFile.FileKey,
                        FileHash = existingFile.FileHash,
                        Message = "文件已存在，无需重复上传"
                    };
                }
                
                logger.LogInformation($"直传去重未命中 - 哈希: {request.FileHash}, 需要上传新文件");
            }

            // 根据前端提供的文件夹路径生成文件Key
            var fileExtension = Path.GetExtension(request.FileName);
            string key;
            
            if (!string.IsNullOrEmpty(request.Folder))
            {
                // 如果前端提供了文件夹，使用指定的文件夹
                var folder = request.Folder.Trim('/');
                key = $"{folder}/{Guid.NewGuid()}{fileExtension}";
            }
            else
            {
                // 如果没有提供文件夹，直接放在存储桶根目录
                key = $"{Guid.NewGuid()}{fileExtension}";
            }
            
            // 使用调用方提供的 Bucket（而不是配置文件中的测试bucket）
            // Bucket参数验证由 Data Annotations 自动处理
            var serviceName = request.Service ?? "default";
            var rustfsBucketName = request.Bucket;  // 使用调用方携带的 bucket 参数
            
            // 记录日志
            var folderInfo = !string.IsNullOrEmpty(request.Folder) ? request.Folder.Trim('/') : "根目录";
            logger.LogInformation($"直传签名生成 - 服务: {serviceName}, Bucket: {rustfsBucketName}, 文件类型: {request.FileType}, 目标位置: {folderInfo}, 生成的Key: {key}");
            
            // 使用存储服务生成上传签名（抽象接口，便于替换存储实现）
            var signature = storageService.GenerateUploadSignature(
                key, request.FileType, rustfsBucketName, 
                expiresInMinutes: 60, maxSizeBytes: maxSizeBytes);
            
            // 获取上传URL（通过存储服务抽象）
            var uploadUrl = storageService.GetUploadUrl(rustfsBucketName);
            logger.LogInformation($"使用上传 URL: {uploadUrl}");

            // 生成AWS4签名所需的字段
            var currentDate = DateTime.UtcNow;
            var dateString = currentDate.ToString("yyyyMMdd");
            var dateTimeString = currentDate.ToString("yyyyMMddTHHmmssZ");
            var credential = $"{_rustFSConfig.AccessKey}/{dateString}/{_rustFSConfig.Region}/s3/aws4_request";

            // 根据文件类型构建对应的代理URL（包含bucket）- 使用存储服务抽象
            var fullFileUrl = storageService.GetProxyUrlByFileType(key, request.FileType, rustfsBucketName);
            
            return new DirectUploadSignatureResponseDto
            {
                Success = true,
                NeedUpload = true,  // 需要上传
                FileKey = key,
                FileUrl = fullFileUrl,  // 返回文件访问URL
                FileHash = request.FileHash,  // 返回哈希值供前端上传完成后使用
                BucketName = rustfsBucketName,  // 返回bucket名称供前端记录时使用
                Signature = new DirectUploadSignatureDto
                {
                    Url = uploadUrl,
                    Key = key,
                    Policy = signature.Policy,
                    Signature = signature.Signature,
                    AccessKeyId = _rustFSConfig.AccessKey,
                    Fields = new Dictionary<string, string>
                    {
                        { "Content-Type", request.FileType },
                        { "x-amz-algorithm", "AWS4-HMAC-SHA256" },
                        { "x-amz-credential", credential },
                        { "x-amz-date", dateTimeString },
                        { "x-amz-signature", signature.Signature }
                    }
                }
            };
        }
        catch (Exception ex)
        {
            logger.LogError($"生成{fileCategory}文件直传签名失败: {ex.Message}");
            logger.LogError($"异常详情: {ex}");

            return new DirectUploadSignatureResponseDto
            {
                Success = false,
                Message = $"生成{fileCategory}文件直传签名失败: {ex.Message}"
            };
        }
    }

    /// <summary>
    /// 生成预签名URL
    /// </summary>
    /// <param name="request">预签名URL请求</param>
    /// <returns>预签名URL响应</returns>
    public async Task<PresignedUrlResponseDto> GetPresignedUrlAsync(PresignedUrlRequestDto request)
    {
        try
        {
            // FileKey和Bucket验证由Data Annotations自动处理
            
            // 验证bucket是否存在
            var bucketExists = await storageService.BucketExistsAsync(request.Bucket);
            if (!bucketExists)
            {
                logger.LogWarning($"存储桶不存在 - Bucket: {request.Bucket}");
                return new PresignedUrlResponseDto
                {
                    Success = false,
                    Message = $"存储桶 '{request.Bucket}' 不存在"
                };
            }
            
            // 生成预签名URL（用于GET请求访问文件）
            var presignedUrl = storageService.GetPresignedUrl(request.Bucket, request.FileKey, false, request.ExpiresInMinutes ?? 60);

            return new PresignedUrlResponseDto
            {
                Success = true,
                PresignedUrl = presignedUrl,
                ExpiresInMinutes = request.ExpiresInMinutes ?? 60
            };
        }
        catch (Exception ex)
        {
            logger.LogError($"生成预签名URL失败: {ex.Message}");
            logger.LogError($"异常详情: {ex}");

            return new PresignedUrlResponseDto
            {
                Success = false,
                Message = $"生成预签名URL失败: {ex.Message}"
            };
        }
    }
    
    /// <summary>
    /// 记录直传完成的文件信息到数据库
    /// </summary>
    /// <param name="request">文件记录请求</param>
    /// <param name="uploaderId">上传者用户ID（可选）</param>
    /// <returns>记录结果</returns>
    public async Task<RecordDirectUploadResponseDto> RecordDirectUploadAsync(RecordDirectUploadRequestDto request, string? uploaderId = null)
    {
        try
        {
            // 验证必填字段
            if (string.IsNullOrEmpty(request.FileHash))
            {
                return new RecordDirectUploadResponseDto
                {
                    Success = false,
                    Message = "文件哈希值不能为空"
                };
            }
            
            if (string.IsNullOrEmpty(request.FileKey))
            {
                return new RecordDirectUploadResponseDto
                {
                    Success = false,
                    Message = "文件Key不能为空"
                };
            }
            
            var serviceName = request.Service ?? "default";
            var bucketName = request.BucketName ?? "default";
            logger.LogInformation($"记录直传文件 - 哈希: {request.FileHash}, Key: {request.FileKey}, 服务: {serviceName}, 存储桶: {bucketName}");
            
            // 创建文件记录（使用 UPSERT 避免并发冲突）
            var uploadedFile = new UploadedFile
            {
                Id = Guid.NewGuid(),
                FileHash = request.FileHash,
                FileKey = request.FileKey,
                FileUrl = request.FileUrl,
                BucketName = request.BucketName ?? "unknown",  // 使用请求中的bucket，如果未提供则标记为unknown
                ReferenceCount = 1,
                UploaderId = uploaderId,
                CreateTime = DateTimeOffset.UtcNow,
                LastAccessTime = DateTimeOffset.UtcNow,
                UploadStatus = (int)UploadStatus.Success, // 直传完成后直接标记为成功
                Deleted = false
            };
            
            // 使用 UPSERT：原子性地插入或更新，自动处理并发冲突
            await dbContext.UpsertToShardTableAsync(uploadedFile);
            
            // 查询最终的记录以获取正确的ID和引用计数
            var finalFile = await dbContext.GetUploadedFilesByHash(request.FileHash)
                .FirstOrDefaultAsync(f => f.FileHash == request.FileHash && !f.Deleted);
            
            logger.LogInformation($"直传文件记录成功 - 哈希: {request.FileHash}, 引用计数: {finalFile?.ReferenceCount ?? 1}");
            
            return new RecordDirectUploadResponseDto
            {
                Success = true,
                Message = "文件记录成功",
                FileId = finalFile?.Id ?? uploadedFile.Id
            };
        }
        catch (Exception ex)
        {
            logger.LogError($"记录直传文件失败: {ex.Message}");
            logger.LogError($"异常详情: {ex}");
            
            return new RecordDirectUploadResponseDto
            {
                Success = false,
                Message = $"记录文件失败: {ex.Message}"
            };
        }
    }

    /// <summary>
    /// 创建目录（已废弃 - 前端不再需要）
    /// </summary>
    /// <param name="request">创建目录请求</param>
    /// <returns>创建结果</returns>
    public Task<AdminResponseDto> CreateDirectoryAsync(CreateDirectoryRequestDto request)
    {
        // 功能已废弃 - 前端不再需要，直接在 RustFS 控制台管理目录
        return Task.FromResult(new AdminResponseDto
        {
            Success = false,
            Message = "此功能已废弃，请直接在 RustFS 控制台管理目录"
        });
    }

    /// <summary>
    /// 根据文件类型获取默认文件夹（仅用于直接上传接口的向后兼容）
    /// </summary>
    private static string GetDefaultFolderByFileType(string fileType)
    {
        return fileType.ToLower() switch
        {
            var type when type.StartsWith("image/") => "images",
            var type when type.StartsWith("video/") => "videos",
            var type when type.StartsWith("audio/") => "audios",
            var type when type.Contains("pdf") || type.Contains("document") || type.Contains("text") => "documents",
            var type when type.Contains("zip") || type.Contains("rar") || type.Contains("7z") || type.Contains("tar") => "archives",
            _ => string.Empty
        };
    }
}

