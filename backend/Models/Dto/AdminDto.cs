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
/// 创建服务请求
/// </summary>
public class CreateServiceRequestDto
{
    /// <summary>
    /// 服务名称
    /// </summary>
    public string Name { get; set; } = string.Empty;
    
    /// <summary>
    /// 服务描述
    /// </summary>
    public string? Description { get; set; }
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
    
    /// <summary>
    /// 所属服务ID
    /// </summary>
    public Guid? ServiceId { get; set; }
    
    /// <summary>
    /// 存储桶描述
    /// </summary>
    public string? Description { get; set; }
}

/// <summary>
/// 服务响应DTO
/// </summary>
public class ServiceResponseDto
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTimeOffset CreateTime { get; set; }
    public DateTimeOffset UpdateTime { get; set; }
    public bool IsEnabled { get; set; }
    public int BucketCount { get; set; }
}

/// <summary>
/// 存储桶响应DTO
/// </summary>
public class BucketResponseDto
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public Guid ServiceId { get; set; }
    public string ServiceName { get; set; } = string.Empty;
    public DateTimeOffset CreateTime { get; set; }
    public DateTimeOffset UpdateTime { get; set; }
    public bool IsEnabled { get; set; }
    public int FileCount { get; set; }
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
/// 创建目录请求DTO
/// </summary>
public class CreateDirectoryRequestDto
{
    /// <summary>
    /// 目录名称
    /// </summary>
    public string DirectoryName { get; set; } = string.Empty;
    
    /// <summary>
    /// 存储桶ID
    /// </summary>
    public Guid BucketId { get; set; }
    
    /// <summary>
    /// 父目录路径（可选，如果不提供则在根目录创建）
    /// </summary>
    public string? ParentPath { get; set; }
}

/// <summary>
/// 目录响应DTO
/// </summary>
public class DirectoryResponseDto
{
    public string Name { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public DateTimeOffset CreateTime { get; set; }
    public int FileCount { get; set; }
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

