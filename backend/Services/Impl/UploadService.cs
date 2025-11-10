using FileService.Constants;
using FileService.Exceptions;
using FileService.Services.Interfaces;
using FileService.Utils;
using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Repositories.Entities;
using Microsoft.EntityFrameworkCore;
using System.Security.Cryptography;
using Microsoft.Extensions.Options;

namespace FileService.Services.Impl;

public class UploadService(
    RustFSUtil rustFSUtil, 
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
    /// 异步上传 IFormFile 到RustFS（带文件去重）
    /// </summary>
    /// <param name="file">前端上传的文件</param>
    /// <param name="uploaderId">上传者用户ID（可选）</param>
    /// <returns>上传成功返回文件的 URL，失败返回 null</returns>
    public async Task<string> UploadFileAsync(IFormFile file, string? uploaderId = null)
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
            
            // 2. 检查数据库中是否已存在相同哈希的文件
            var existingFile = await dbContext.UploadedFiles
                .AsTracking() // 需要修改实体，必须跟踪
                .FirstOrDefaultAsync(f => f.FileHash == fileHash && !f.Deleted);
            
            if (existingFile != null)
            {
                // 文件已存在，更新引用计数和最后访问时间
                existingFile.ReferenceCount++;
                existingFile.LastAccessTime = DateTimeOffset.UtcNow;
                await dbContext.SaveChangesAsync();
                
                logger.LogInformation($"文件已存在（去重），哈希: {fileHash}, 引用计数: {existingFile.ReferenceCount}, 返回URL: {existingFile.FileUrl}");
                return existingFile.FileUrl;
            }
            
            // 3. 文件不存在，执行上传
            // 根据文件类型获取对应的子目录
            var folder = rustFSUtil.GetFolderByFileType(file.ContentType);
            
            // 生成带有子目录的文件名（使用哈希值的前16位+扩展名，更易识别）
            var fileExtension = Path.GetExtension(file.FileName);
            var fileName = $"{folder}/{fileHash[..16]}_{Guid.NewGuid():N}{fileExtension}";
            logger.LogInformation($"上传新文件: {fileName}");
            
            // 获取统一存储桶
            var bucket = rustFSUtil.GetBucketByFileType(file.ContentType);
            
            // 上传到统一存储桶的对应子目录
            memoryStream.Position = 0;
            await rustFSUtil.UploadStreamAsync(memoryStream, fileName, bucket);
            
            // 根据文件类型返回对应的代理URL
            var proxyFileUrl = rustFSUtil.GetProxyUrlByFileType(fileName, file.ContentType);
            
            // 4. 保存文件记录到数据库
            var uploadedFile = new UploadedFile
            {
                Id = Guid.NewGuid(),
                FileHash = fileHash,
                FileKey = fileName,
                FileUrl = proxyFileUrl,
                OriginalFileName = file.FileName,
                FileSize = file.Length,
                ContentType = file.ContentType,
                FileExtension = fileExtension,
                BucketName = bucket,
                ReferenceCount = 1,
                UploaderId = uploaderId, // 记录上传者ID
                CreateTime = DateTimeOffset.UtcNow,
                LastAccessTime = DateTimeOffset.UtcNow,
                Deleted = false
            };
            
            dbContext.UploadedFiles.Add(uploadedFile);
            await dbContext.SaveChangesAsync();
            
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
            if (string.IsNullOrEmpty(request.FileName))
            {
                return new DirectUploadSignatureResponseDto
                {
                    Success = false,
                    Message = "文件名不能为空"
                };
            }

            if (string.IsNullOrEmpty(request.FileType))
            {
                return new DirectUploadSignatureResponseDto
                {
                    Success = false,
                    Message = "文件类型不能为空"
                };
            }
            
            //  去重检查：如果提供了文件哈希，先检查数据库中是否已存在
            // 注意：去重检查需要考虑服务名，不同服务的文件可以重复（但同一服务内不能重复）
            if (!string.IsNullOrEmpty(request.FileHash))
            {
                var service = request.Service ?? string.Empty;
                logger.LogInformation($"直传去重检查 - 文件哈希: {request.FileHash}, 文件名: {request.FileName}, 服务: {service}");
                
                // 使用分表查询
                var existingFile = await dbContext
                    .GetUploadedFilesByService(service)
                    .AsTracking()
                    .FirstOrDefaultAsync(f => f.FileHash == request.FileHash && !f.Deleted);
                
                if (existingFile != null)
                {
                    // 文件已存在，更新引用计数并返回已有URL
                    existingFile.ReferenceCount++;
                    existingFile.LastAccessTime = DateTimeOffset.UtcNow;
                    await dbContext.SaveChangesAsync();
                    
                    logger.LogInformation($"直传去重命中 - 哈希: {request.FileHash}, 引用计数: {existingFile.ReferenceCount}, 返回URL: {existingFile.FileUrl}");
                    
                    return new DirectUploadSignatureResponseDto
                    {
                        Success = true,
                        NeedUpload = false,  //告诉前端无需上传
                        ExistingFileUrl = existingFile.FileUrl,
                        FileKey = existingFile.FileKey,
                        FileHash = existingFile.FileHash,
                        Message = "文件已存在，无需重复上传"
                    };
                }
                
                logger.LogInformation($"直传去重未命中 - 哈希: {request.FileHash}, 需要上传新文件");
            }

            // 根据文件类型获取对应的子目录
            var folder = rustFSUtil.GetFolderByFileType(request.FileType);
            
            // 生成带有子目录的文件Key
            var fileExtension = Path.GetExtension(request.FileName);
            var key = $"{folder}/{Guid.NewGuid()}{fileExtension}";
            
            // 记录日志
            logger.LogInformation($"直传签名生成 - 文件类型: {request.FileType}, 子目录: {folder}, 生成的Key: {key}");
            
            // 获取统一存储桶
            var bucket = rustFSUtil.GetBucketByFileType(request.FileType);
            
            // 生成S3 POST策略签名
            var signature = rustFSUtil.GeneratePostPolicySignature(key, request.FileType, expiresInMinutes: 60, maxSizeBytes: maxSizeBytes);
            
            // 构建上传URL
            // 生产环境：使用 Nginx 代理路径（避免直接暴露 MinIO 端口）
            // 格式：https://www.archi0v0.top/upload/blog-files
            string uploadUrl;
            
            if (!string.IsNullOrEmpty(_rustFSConfig.ProxyPath) && _rustFSConfig.ProxyPath.Contains("/api/files"))
            {
                // 使用代理路径（去掉 /api/files，改为 /upload）
                var baseUrl = _rustFSConfig.ProxyPath.Replace("/api/files", "/upload");
                uploadUrl = $"{baseUrl}/{bucket}";
                logger.LogInformation($"使用 Nginx 代理上传 URL: {uploadUrl}");
            }
            else
            {
                // 开发环境或无代理：直接使用 MinIO 端点
                uploadUrl = $"{(_rustFSConfig.UseHttps ? "https" : "http")}://{_rustFSConfig.Endpoint}/{bucket}";
                logger.LogInformation($"使用直接 MinIO 端点: {uploadUrl}");
            }

            // 生成AWS4签名所需的字段
            var currentDate = DateTime.UtcNow;
            var dateString = currentDate.ToString("yyyyMMdd");
            var dateTimeString = currentDate.ToString("yyyyMMddTHHmmssZ");
            var credential = $"{_rustFSConfig.AccessKey}/{dateString}/{_rustFSConfig.Region}/s3/aws4_request";

            // 根据文件类型构建对应的代理URL
            var fullFileUrl = rustFSUtil.GetProxyUrlByFileType(key, request.FileType);
            
            return new DirectUploadSignatureResponseDto
            {
                Success = true,
                NeedUpload = true,  // 需要上传
                FileKey = key,
                FileHash = request.FileHash,  // 返回哈希值供前端上传完成后使用
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
    public Task<PresignedUrlResponseDto> GetPresignedUrlAsync(PresignedUrlRequestDto request)
    {
        try
        {
            if (string.IsNullOrEmpty(request.FileKey))
            {
                return Task.FromResult(new PresignedUrlResponseDto
                {
                    Success = false,
                    Message = "文件Key不能为空"
                });
            }
            
            // 生成预签名URL（用于GET请求访问文件）
            var presignedUrl = rustFSUtil.GetPresignedUrl(request.FileKey, request.ExpiresInMinutes ?? 60);

            return Task.FromResult(new PresignedUrlResponseDto
            {
                Success = true,
                PresignedUrl = presignedUrl,
                ExpiresInMinutes = request.ExpiresInMinutes ?? 60
            });
        }
        catch (Exception ex)
        {
            logger.LogError($"生成预签名URL失败: {ex.Message}");
            logger.LogError($"异常详情: {ex}");

            return Task.FromResult(new PresignedUrlResponseDto
            {
                Success = false,
                Message = $"生成预签名URL失败: {ex.Message}"
            });
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
            
            var service = request.Service ?? string.Empty;
            logger.LogInformation($"记录直传文件 - 哈希: {request.FileHash}, Key: {request.FileKey}, 服务: {service}");
            
            // 检查是否已存在相同哈希的文件（理论上不应该存在，因为前面已检查过）
            // 注意：去重检查需要考虑服务名，不同服务的文件可以重复（但同一服务内不能重复）
            // 使用分表查询
            var existingFile = await dbContext
                .GetUploadedFilesByService(service)
                .AsTracking()
                .FirstOrDefaultAsync(f => f.FileHash == request.FileHash && !f.Deleted);
            
            if (existingFile != null)
            {
                // 如果已存在（可能是并发上传），只更新引用计数
                logger.LogWarning($"文件已存在但仍被上传 - 哈希: {request.FileHash}, 可能是并发上传");
                existingFile.ReferenceCount++;
                existingFile.LastAccessTime = DateTimeOffset.UtcNow;
                await dbContext.SaveChangesAsync();
                
                return new RecordDirectUploadResponseDto
                {
                    Success = true,
                    Message = "文件已存在，已更新引用计数",
                    FileId = existingFile.Id
                };
            }
            
            // 创建新的文件记录
            var uploadedFile = new UploadedFile
            {
                Id = Guid.NewGuid(),
                FileHash = request.FileHash,
                FileKey = request.FileKey,
                FileUrl = request.FileUrl,
                OriginalFileName = request.OriginalFileName,
                FileSize = request.FileSize,
                ContentType = request.ContentType,
                FileExtension = Path.GetExtension(request.OriginalFileName),
                BucketName = request.BucketName ?? string.Empty,
                ReferenceCount = 1,
                UploaderId = uploaderId, // 记录上传者ID
                Service = service, // 记录服务来源
                CreateTime = DateTimeOffset.UtcNow,
                LastAccessTime = DateTimeOffset.UtcNow,
                Deleted = false
            };
            
            // 使用分表插入
            await dbContext.AddUploadedFileToServiceTableAsync(uploadedFile, service);
            
            logger.LogInformation($"直传文件记录成功 - ID: {uploadedFile.Id}, 哈希: {request.FileHash}");
            
            return new RecordDirectUploadResponseDto
            {
                Success = true,
                Message = "文件记录成功",
                FileId = uploadedFile.Id
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
}

