using FileService.Models.Dto;

namespace FileService.Services.Interfaces;

/// <summary>
/// 同步服务接口：处理 RustFS 和数据库之间的数据一致性
/// </summary>
public interface ISyncService
{
    /// <summary>
    /// 同步 RustFS 中的文件到数据库
    /// 扫描指定 bucket，为没有数据库记录的文件创建记录
    /// </summary>
    /// <param name="bucketName">要同步的 bucket 名称</param>
    /// <param name="prefix">文件前缀过滤（可选）</param>
    /// <returns>同步结果</returns>
    Task<SyncResultDto> SyncRustFSToDatabase(string bucketName, string? prefix = null);
    
    /// <summary>
    /// 清理数据库中存在但 RustFS 中不存在的记录
    /// </summary>
    /// <param name="bucketName">要检查的 bucket 名称</param>
    /// <returns>清理结果</returns>
    Task<SyncResultDto> CleanOrphanedRecords(string bucketName);
    
    /// <summary>
    /// 获取同步状态报告
    /// </summary>
    /// <param name="bucketName">要检查的 bucket 名称</param>
    /// <returns>同步状态</returns>
    Task<SyncStatusDto> GetSyncStatus(string bucketName);
}
