namespace FileService.Models.Dto;

/// <summary>
/// 同步结果 DTO
/// </summary>
public class SyncResultDto
{
    /// <summary>
    /// 是否成功
    /// </summary>
    public bool Success { get; set; }
    
    /// <summary>
    /// 消息
    /// </summary>
    public string Message { get; set; } = string.Empty;
    
    /// <summary>
    /// 扫描的文件总数
    /// </summary>
    public int TotalFiles { get; set; }
    
    /// <summary>
    /// 已同步的文件数
    /// </summary>
    public int SyncedFiles { get; set; }
    
    /// <summary>
    /// 跳过的文件数（已存在）
    /// </summary>
    public int SkippedFiles { get; set; }
    
    /// <summary>
    /// 失败的文件数
    /// </summary>
    public int FailedFiles { get; set; }
    
    /// <summary>
    /// 详细日志
    /// </summary>
    public List<string> Details { get; set; } = new();
}

/// <summary>
/// 同步状态 DTO
/// </summary>
public class SyncStatusDto
{
    /// <summary>
    /// Bucket 名称
    /// </summary>
    public string BucketName { get; set; } = string.Empty;
    
    /// <summary>
    /// RustFS 中的文件数
    /// </summary>
    public int RustFSFileCount { get; set; }
    
    /// <summary>
    /// 数据库中的记录数
    /// </summary>
    public int DatabaseRecordCount { get; set; }
    
    /// <summary>
    /// 缺失数据库记录的文件数
    /// </summary>
    public int MissingRecordCount { get; set; }
    
    /// <summary>
    /// 孤儿记录数（数据库有记录但文件不存在）
    /// </summary>
    public int OrphanedRecordCount { get; set; }
    
    /// <summary>
    /// 是否需要同步
    /// </summary>
    public bool NeedSync => MissingRecordCount > 0 || OrphanedRecordCount > 0;
}
