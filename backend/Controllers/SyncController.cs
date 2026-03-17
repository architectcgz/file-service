using Microsoft.AspNetCore.Mvc;
using FileService.Services.Interfaces;
using FileService.Attributes;

namespace FileService.Controllers;

/// <summary>
/// 同步管理控制器
/// </summary>
[ApiController]
[Route("api/admin/sync")]
// [AdminApiKey] // 已配置在后端，前端无需传递
public class SyncController : ControllerBase
{
    private readonly ISyncService _syncService;
    private readonly ILogger<SyncController> _logger;

    public SyncController(ISyncService syncService, ILogger<SyncController> logger)
    {
        _syncService = syncService;
        _logger = logger;
    }

    /// <summary>
    /// 获取同步状态
    /// </summary>
    /// <param name="bucketName">Bucket 名称</param>
    [HttpGet("status")]
    public async Task<IActionResult> GetSyncStatus([FromQuery] string bucketName)
    {
        if (string.IsNullOrWhiteSpace(bucketName))
        {
            return BadRequest(new { success = false, message = "Bucket 名称不能为空" });
        }

        var status = await _syncService.GetSyncStatus(bucketName);
        return Ok(new
        {
            success = true,
            data = status
        });
    }

    /// <summary>
    /// 同步 RustFS 文件到数据库
    /// 扫描指定 bucket，为没有数据库记录的文件创建记录
    /// </summary>
    /// <param name="bucketName">Bucket 名称</param>
    /// <param name="prefix">文件前缀（可选，用于过滤特定目录）</param>
    [HttpPost("rustfs-to-db")]
    public async Task<IActionResult> SyncRustFSToDatabase(
        [FromQuery] string bucketName,
        [FromQuery] string? prefix = null)
    {
        if (string.IsNullOrWhiteSpace(bucketName))
        {
            return BadRequest(new { success = false, message = "Bucket 名称不能为空" });
        }

        _logger.LogInformation($"开始同步 RustFS 到数据库 - Bucket: {bucketName}, Prefix: {prefix}");

        var result = await _syncService.SyncRustFSToDatabase(bucketName, prefix);

        return Ok(new
        {
            success = result.Success,
            message = result.Message,
            data = new
            {
                totalFiles = result.TotalFiles,
                syncedFiles = result.SyncedFiles,
                skippedFiles = result.SkippedFiles,
                failedFiles = result.FailedFiles,
                details = result.Details
            }
        });
    }

    /// <summary>
    /// 清理孤儿记录
    /// 标记数据库中存在但 RustFS 中不存在的文件记录为已删除
    /// </summary>
    /// <param name="bucketName">Bucket 名称</param>
    [HttpPost("clean-orphaned")]
    public async Task<IActionResult> CleanOrphanedRecords([FromQuery] string bucketName)
    {
        if (string.IsNullOrWhiteSpace(bucketName))
        {
            return BadRequest(new { success = false, message = "Bucket 名称不能为空" });
        }

        _logger.LogInformation($"开始清理孤儿记录 - Bucket: {bucketName}");

        var result = await _syncService.CleanOrphanedRecords(bucketName);

        return Ok(new
        {
            success = result.Success,
            message = result.Message,
            data = new
            {
                totalRecords = result.TotalFiles,
                cleanedRecords = result.SyncedFiles,
                skippedRecords = result.SkippedFiles,
                failedRecords = result.FailedFiles,
                details = result.Details
            }
        });
    }

    /// <summary>
    /// 完整同步（同步 RustFS 到 DB + 清理孤儿记录）
    /// </summary>
    /// <param name="bucketName">Bucket 名称</param>
    [HttpPost("full-sync")]
    public async Task<IActionResult> FullSync([FromQuery] string bucketName)
    {
        if (string.IsNullOrWhiteSpace(bucketName))
        {
            return BadRequest(new { success = false, message = "Bucket 名称不能为空" });
        }

        _logger.LogInformation($"开始完整同步 - Bucket: {bucketName}");

        // 1. 先同步 RustFS 到数据库
        var syncResult = await _syncService.SyncRustFSToDatabase(bucketName, null);

        // 2. 再清理孤儿记录
        var cleanResult = await _syncService.CleanOrphanedRecords(bucketName);

        return Ok(new
        {
            success = syncResult.Success && cleanResult.Success,
            message = "完整同步完成",
            data = new
            {
                sync = new
                {
                    totalFiles = syncResult.TotalFiles,
                    syncedFiles = syncResult.SyncedFiles,
                    skippedFiles = syncResult.SkippedFiles,
                    failedFiles = syncResult.FailedFiles
                },
                clean = new
                {
                    totalRecords = cleanResult.TotalFiles,
                    cleanedRecords = cleanResult.SyncedFiles,
                    skippedRecords = cleanResult.SkippedFiles,
                    failedRecords = cleanResult.FailedFiles
                }
            }
        });
    }
}
