using System.ComponentModel.DataAnnotations;

namespace FileService.Models.Dto;

/// <summary>
/// 直传签名请求DTO
/// </summary>
public class DirectUploadSignatureRequestDto
{
    /// <summary>
    /// 文件名
    /// </summary>
    [Required(ErrorMessage = "文件名不能为空")]
    public string FileName { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件类型
    /// </summary>
    [Required(ErrorMessage = "文件类型不能为空")]
    public string FileType { get; set; } = string.Empty;
    
    /// <summary>
    /// 存储桶名称（由调用方提供，不使用配置文件中的测试bucket）
    /// </summary>
    [Required(ErrorMessage = "存储桶名称不能为空")]
    [MinLength(3, ErrorMessage = "存储桶名称至少3个字符")]
    [MaxLength(63, ErrorMessage = "存储桶名称最多63个字符")]
    [RegularExpression(@"^[a-z0-9][a-z0-9\-]*[a-z0-9]$", ErrorMessage = "存储桶名称格式不正确，只能包含小写字母、数字和连字符，且不能以连字符开头或结尾")]
    public string Bucket { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件SHA256哈希值（用于去重检查）
    /// </summary>
    public string? FileHash { get; set; }
    
    /// <summary>
    /// 文件大小（字节）
    /// </summary>
    [Range(1, 10737418240, ErrorMessage = "文件大小必须在1字节到10GB之间")]  // 最大10GB
    public long? FileSize { get; set; }
    
    /// <summary>
    /// 服务来源标识（例如：blog, market, admin 等）
    /// </summary>
    public string? Service { get; set; }
    
    /// <summary>
    /// 文件夹路径（可选，如果不提供则根据文件类型自动判断）
    /// </summary>
    public string? Folder { get; set; }
}

/// <summary>
/// 直传签名响应DTO
/// </summary>
public class DirectUploadSignatureResponseDto
{
    /// <summary>
    /// 是否成功
    /// </summary>
    public bool Success { get; set; }
    
    /// <summary>
    /// 错误消息
    /// </summary>
    public string? Message { get; set; }
    
    /// <summary>
    /// 签名信息（如果需要上传）
    /// </summary>
    public DirectUploadSignatureDto? Signature { get; set; }
    
    /// <summary>
    /// 已存在的文件URL（如果文件已存在则直接返回）
    /// </summary>
    public string? ExistingFileUrl { get; set; }
    
    /// <summary>
    /// 文件Key（用于前端引用）
    /// </summary>
    public string? FileKey { get; set; }
    
    /// <summary>
    /// 是否需要上传（false表示文件已存在，无需上传）
    /// </summary>
    public bool NeedUpload { get; set; } = true;
    
    /// <summary>
    /// 文件哈希值（返回给前端用于记录）
    /// </summary>
    public string? FileHash { get; set; }
    
    /// <summary>
    /// 文件访问URL
    /// </summary>
    public string? FileUrl { get; set; }
    
    /// <summary>
    /// 存储桶名称（用于前端记录时传回）
    /// </summary>
    public string? BucketName { get; set; }
}

/// <summary>
/// 直传签名DTO
/// </summary>
public class DirectUploadSignatureDto
{
    /// <summary>
    /// 上传URL
    /// </summary>
    public string Url { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件Key
    /// </summary>
    public string Key { get; set; } = string.Empty;
    
    /// <summary>
    /// 策略
    /// </summary>
    public string Policy { get; set; } = string.Empty;
    
    /// <summary>
    /// 签名
    /// </summary>
    public string Signature { get; set; } = string.Empty;
    
    /// <summary>
    /// 访问密钥ID
    /// </summary>
    public string AccessKeyId { get; set; } = string.Empty;
    
    /// <summary>
    /// 其他字段
    /// </summary>
    public Dictionary<string, string> Fields { get; set; } = new();
}

/// <summary>
/// 记录直传完成文件的请求DTO
/// </summary>
public class RecordDirectUploadRequestDto
{
    /// <summary>
    /// 文件SHA256哈希值
    /// </summary>
    [Required(ErrorMessage = "文件哈希值不能为空")]
    public string FileHash { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件Key（存储路径）
    /// </summary>
    [Required(ErrorMessage = "文件Key不能为空")]
    public string FileKey { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件URL
    /// </summary>
    [Required(ErrorMessage = "文件URL不能为空")]
    public string FileUrl { get; set; } = string.Empty;
    
    /// <summary>
    /// 原始文件名
    /// </summary>
    [Required(ErrorMessage = "原始文件名不能为空")]
    public string OriginalFileName { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件大小（字节）
    /// </summary>
    [Required(ErrorMessage = "文件大小不能为空")]
    [Range(1, long.MaxValue, ErrorMessage = "文件大小必须大于0")]
    public long FileSize { get; set; }
    
    /// <summary>
    /// 文件MIME类型
    /// </summary>
    [Required(ErrorMessage = "文件类型不能为空")]
    public string ContentType { get; set; } = string.Empty;
    
    /// <summary>
    /// 存储桶名称
    /// </summary>
    public string? BucketName { get; set; }
    
    /// <summary>
    /// 服务来源标识（例如：blog, market, admin 等）
    /// </summary>
    public string? Service { get; set; }
}

/// <summary>
/// 记录直传完成文件的响应DTO
/// </summary>
public class RecordDirectUploadResponseDto
{
    /// <summary>
    /// 是否成功
    /// </summary>
    public bool Success { get; set; }
    
    /// <summary>
    /// 消息
    /// </summary>
    public string? Message { get; set; }
    
    /// <summary>
    /// 文件记录ID
    /// </summary>
    public Guid? FileId { get; set; }
}

/// <summary>
/// 预签名URL请求DTO
/// </summary>
public class PresignedUrlRequestDto
{
    /// <summary>
    /// 文件Key
    /// </summary>
    [Required(ErrorMessage = "文件Key不能为空")]
    public string FileKey { get; set; } = string.Empty;
    
    /// <summary>
    /// 存储桶名称
    /// </summary>
    [Required(ErrorMessage = "存储桶名称不能为空")]
    [MinLength(3, ErrorMessage = "存储桶名称至少3个字符")]
    [MaxLength(63, ErrorMessage = "存储桶名称最多63个字符")]
    public string Bucket { get; set; } = string.Empty;
    
    /// <summary>
    /// 过期时间（分钟），默认60分钟
    /// </summary>
    [Range(1, 10080, ErrorMessage = "过期时间必须在1分钟到7天之间")]
    public int? ExpiresInMinutes { get; set; } = 60;
}

/// <summary>
/// 预签名URL响应DTO
/// </summary>
public class PresignedUrlResponseDto
{
    /// <summary>
    /// 是否成功
    /// </summary>
    public bool Success { get; set; }
    
    /// <summary>
    /// 错误消息
    /// </summary>
    public string? Message { get; set; }
    
    /// <summary>
    /// 预签名URL
    /// </summary>
    public string? PresignedUrl { get; set; }
    
    /// <summary>
    /// 过期时间（分钟）
    /// </summary>
    public int ExpiresInMinutes { get; set; }
}

