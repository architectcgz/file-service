namespace FileService.Repositories.Entities;

/// <summary>
/// 文件上传状态枚举
/// </summary>
public enum UploadStatus
{
    /// <summary>
    /// 上传中（数据库记录已创建，但文件尚未上传到存储）
    /// </summary>
    Uploading = 0,
    
    /// <summary>
    /// 上传成功（文件已成功上传到存储）
    /// </summary>
    Success = 1,
    
    /// <summary>
    /// 上传失败（文件上传到存储失败）
    /// </summary>
    Failed = 2
}
