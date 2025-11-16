using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Repositories.Entities;
using FileService.Services.Interfaces;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace FileService.Services.Impl;

/// <summary>
/// 同步服务实现：处理存储和数据库之间的数据一致性
/// </summary>
public class SyncService : ISyncService
{
    private readonly IStorageService _storageService;
    private readonly FileServiceDbContext _dbContext;
    private readonly ILogger<SyncService> _logger;

    public SyncService(
        IStorageService storageService,
        FileServiceDbContext dbContext,
        ILogger<SyncService> logger)
    {
        _storageService = storageService;
        _dbContext = dbContext;
        _logger = logger;
    }

    /// <summary>
    /// 同步 RustFS 中的文件到数据库
    /// </summary>
    public async Task<SyncResultDto> SyncRustFSToDatabase(string bucketName, string? prefix = null)
    {
        var result = new SyncResultDto();
        
        try
        {
            _logger.LogInformation($"开始同步 bucket: {bucketName}, prefix: {prefix ?? "全部"}");
            
            // 1. 列举存储中的所有文件
            var s3Objects = await _storageService.ListObjectsAsync(bucketName, prefix);
            result.TotalFiles = s3Objects.Count;
            
            _logger.LogInformation($"找到 {result.TotalFiles} 个文件");
            
            // 2. 遍历每个文件，检查是否存在数据库记录
            foreach (var s3Object in s3Objects)
            {
                try
                {
                    var fileKey = s3Object.Key;
                    
                    // 检查数据库中是否已存在该文件（通过 file_key）
                    var existing = await _dbContext.UploadedFiles
                        .FirstOrDefaultAsync(f => f.FileKey == fileKey && f.BucketName == bucketName && !f.Deleted);
                    
                    if (existing != null)
                    {
                        result.SkippedFiles++;
                        result.Details.Add($"跳过: {fileKey} (已存在)");
                        continue;
                    }
                    
                    // 3. 创建数据库记录
                    // 注意：因为文件已经存在于存储，我们无法获取原始文件哈希
                    // 可以使用 ETag（如果是简单上传）或下载文件计算哈希
                    var fileUrl = _storageService.GetProxyUrl(fileKey, bucketName);
                    
                    var uploadedFile = new UploadedFile
                    {
                        Id = Guid.NewGuid(),
                        FileHash = s3Object.ETag.Trim('"'), // ETag 通常是 MD5（简单上传）或分段上传的标识
                        FileKey = fileKey,
                        FileUrl = fileUrl,
                        BucketName = bucketName,
                        ReferenceCount = 1,
                        UploaderId = "system-sync", // 系统同步的标记
                        CreateTime = s3Object.LastModified,
                        LastAccessTime = DateTimeOffset.UtcNow,
                        UploadStatus = (int)UploadStatus.Success,
                        Deleted = false
                    };
                    
                    // 使用 UPSERT 避免并发冲突
                    await _dbContext.UpsertToShardTableAsync(uploadedFile);
                    
                    result.SyncedFiles++;
                    result.Details.Add($"同步成功: {fileKey}");
                    
                    _logger.LogInformation($"同步文件: {fileKey}");
                }
                catch (Exception ex)
                {
                    result.FailedFiles++;
                    result.Details.Add($"同步失败: {s3Object.Key} - {ex.Message}");
                    _logger.LogError(ex, $"同步文件失败: {s3Object.Key}");
                }
            }
            
            result.Success = result.FailedFiles == 0;
            result.Message = $"同步完成。总计: {result.TotalFiles}, 成功: {result.SyncedFiles}, 跳过: {result.SkippedFiles}, 失败: {result.FailedFiles}";
            
            _logger.LogInformation(result.Message);
        }
        catch (Exception ex)
        {
            result.Success = false;
            result.Message = $"同步失败: {ex.Message}";
            _logger.LogError(ex, "同步过程出错");
        }
        
        return result;
    }

    /// <summary>
    /// 清理孤儿记录（数据库有记录但文件不存在）
    /// </summary>
    public async Task<SyncResultDto> CleanOrphanedRecords(string bucketName)
    {
        var result = new SyncResultDto();
        
        try
        {
            _logger.LogInformation($"开始清理 bucket: {bucketName} 的孤儿记录");
            
            // 1. 获取数据库中该 bucket 的所有记录
            var dbRecords = await _dbContext.UploadedFiles
                .Where(f => f.BucketName == bucketName && !f.Deleted)
                .ToListAsync();
            
            result.TotalFiles = dbRecords.Count;
            _logger.LogInformation($"找到 {result.TotalFiles} 条数据库记录");
            
            // 2. 检查每条记录对应的文件是否存在于 RustFS
            foreach (var record in dbRecords)
            {
                try
                {
                    var exists = await _storageService.FileExistsAsync(bucketName, record.FileKey);
                    
                    if (exists)
                    {
                        result.SkippedFiles++;
                        continue;
                    }
                    
                    // 文件不存在，标记为已删除
                    record.Deleted = true;
                    result.SyncedFiles++;
                    result.Details.Add($"清理孤儿记录: {record.FileKey}");
                    
                    _logger.LogInformation($"清理孤儿记录: {record.FileKey}");
                }
                catch (Exception ex)
                {
                    result.FailedFiles++;
                    result.Details.Add($"检查失败: {record.FileKey} - {ex.Message}");
                    _logger.LogError(ex, $"检查文件失败: {record.FileKey}");
                }
            }
            
            // 保存更改
            await _dbContext.SaveChangesAsync();
            
            result.Success = result.FailedFiles == 0;
            result.Message = $"清理完成。总计: {result.TotalFiles}, 清理: {result.SyncedFiles}, 跳过: {result.SkippedFiles}, 失败: {result.FailedFiles}";
            
            _logger.LogInformation(result.Message);
        }
        catch (Exception ex)
        {
            result.Success = false;
            result.Message = $"清理失败: {ex.Message}";
            _logger.LogError(ex, "清理过程出错");
        }
        
        return result;
    }

    /// <summary>
    /// 获取同步状态
    /// </summary>
    public async Task<SyncStatusDto> GetSyncStatus(string bucketName)
    {
        var status = new SyncStatusDto
        {
            BucketName = bucketName
        };
        
        try
        {
            // 1. 获取存储中的文件数
            var s3Objects = await _storageService.ListObjectsAsync(bucketName, null);
            status.RustFSFileCount = s3Objects.Count;
            
            // 2. 获取数据库中的记录数
            status.DatabaseRecordCount = await _dbContext.UploadedFiles
                .Where(f => f.BucketName == bucketName && !f.Deleted)
                .CountAsync();
            
            // 3. 简单计算缺失记录数（粗略估计）
            status.MissingRecordCount = Math.Max(0, status.RustFSFileCount - status.DatabaseRecordCount);
            status.OrphanedRecordCount = Math.Max(0, status.DatabaseRecordCount - status.RustFSFileCount);
            
            _logger.LogInformation($"同步状态 - Bucket: {bucketName}, RustFS: {status.RustFSFileCount}, DB: {status.DatabaseRecordCount}");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, $"获取同步状态失败: {bucketName}");
        }
        
        return status;
    }
}
