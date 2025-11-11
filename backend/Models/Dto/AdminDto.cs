namespace FileService.Models.Dto;

/// <summary>
/// 创建存储表请求
/// </summary>
public class CreateTableRequestDto
{
    /// <summary>
    /// 服务名称
    /// </summary>
    public string Service { get; set; } = string.Empty;
}

/// <summary>
/// 创建存储桶请求
/// </summary>
public class CreateBucketRequestDto
{
    /// <summary>
    /// 存储桶名称
    /// </summary>
    public string BucketName { get; set; } = string.Empty;
}

/// <summary>
/// 删除文件请求
/// </summary>
public class DeleteFileRequestDto
{
    /// <summary>
    /// 文件Key
    /// </summary>
    public string FileKey { get; set; } = string.Empty;

    /// <summary>
    /// 存储桶名称（可选，如果不提供则使用默认存储桶）
    /// </summary>
    public string? BucketName { get; set; }
}

/// <summary>
/// 登录请求
/// </summary>
public class AdminLoginRequestDto
{
    /// <summary>
    /// 用户名
    /// </summary>
    public string Username { get; set; } = string.Empty;

    /// <summary>
    /// 密码
    /// </summary>
    public string Password { get; set; } = string.Empty;
}

/// <summary>
/// 通用响应DTO
/// </summary>
public class AdminResponseDto
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public object? Data { get; set; }
}

