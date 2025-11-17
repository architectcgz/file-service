namespace FileService.Models.Dto;

/// <summary>
/// 签名颁发请求
/// </summary>
public class SignatureIssueRequestDto
{
    /// <summary>
    /// 调用方服务名称
    /// </summary>
    public string CallerService { get; set; } = string.Empty;

    /// <summary>
    /// 调用方服务ID（可选）
    /// </summary>
    public string? CallerServiceId { get; set; }

    /// <summary>
    /// 允许的操作类型（upload, download, delete, list, *）
    /// </summary>
    public string AllowedOperation { get; set; } = "upload";

    /// <summary>
    /// 允许的文件类型（多个用逗号分隔）
    /// </summary>
    public string? AllowedFileTypes { get; set; }

    /// <summary>
    /// 最大文件大小（字节）
    /// </summary>
    public long? MaxFileSize { get; set; }

    /// <summary>
    /// 过期时间（分钟，默认60分钟）
    /// </summary>
    public int ExpiryMinutes { get; set; } = 60;

    /// <summary>
    /// 最大使用次数（0表示无限制）
    /// </summary>
    public int MaxUsageCount { get; set; } = 0;

    /// <summary>
    /// 备注信息
    /// </summary>
    public string? Notes { get; set; }
}

/// <summary>
/// 签名颁发响应
/// </summary>
public class SignatureIssueResponseDto
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public string? SignatureToken { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public long SignatureId { get; set; }
}

/// <summary>
/// 签名验证结果
/// </summary>
public class SignatureValidationResultDto
{
    public bool IsValid { get; set; }
    public string Message { get; set; } = string.Empty;
    public long? SignatureId { get; set; }
    public string? CallerService { get; set; }
    public string? AllowedOperation { get; set; }
    public long? MaxFileSize { get; set; }
}

/// <summary>
/// 签名查询参数
/// </summary>
public class SignatureQueryDto
{
    /// <summary>
    /// 页码（从1开始）
    /// </summary>
    public int PageIndex { get; set; } = 1;

    /// <summary>
    /// 每页数量
    /// </summary>
    public int PageSize { get; set; } = 20;

    /// <summary>
    /// 调用方服务名称（模糊查询）
    /// </summary>
    public string? CallerService { get; set; }

    /// <summary>
    /// 状态筛选（active, expired, revoked）
    /// </summary>
    public string? Status { get; set; }

    /// <summary>
    /// 操作类型筛选
    /// </summary>
    public string? Operation { get; set; }

    /// <summary>
    /// 开始日期
    /// </summary>
    public DateTime? StartDate { get; set; }

    /// <summary>
    /// 结束日期
    /// </summary>
    public DateTime? EndDate { get; set; }
}

/// <summary>
/// 分页结果
/// </summary>
public class PagedResultDto<T>
{
    public List<T> Items { get; set; } = new();
    public int TotalCount { get; set; }
    public int PageIndex { get; set; }
    public int PageSize { get; set; }
    public int TotalPages => (int)Math.Ceiling((double)TotalCount / PageSize);
}

/// <summary>
/// 签名统计信息
/// </summary>
public class SignatureStatisticsDto
{
    public int TotalSignatures { get; set; }
    public int ActiveSignatures { get; set; }
    public int ExpiredSignatures { get; set; }
    public int RevokedSignatures { get; set; }
    public int TodayIssued { get; set; }
    public int TodayUsed { get; set; }
    public Dictionary<string, int> SignaturesByService { get; set; } = new();
    public Dictionary<string, int> SignaturesByOperation { get; set; } = new();
}

/// <summary>
/// 批量撤销请求
/// </summary>
public class BatchRevokeRequestDto
{
    public List<string> SignatureTokens { get; set; } = new();
    public string Reason { get; set; } = string.Empty;
}

/// <summary>
/// 更新过期时间请求
/// </summary>
public class UpdateExpiryRequestDto
{
    public string SignatureToken { get; set; } = string.Empty;
    public DateTime NewExpiryTime { get; set; }
}
