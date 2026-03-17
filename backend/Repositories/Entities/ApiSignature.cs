using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace FileService.Repositories.Entities;

/// <summary>
/// API签名实体 - 用于服务间调用的临时签名管理
/// </summary>
[Table("api_signatures")]
public class ApiSignature
{
    /// <summary>
    /// 签名ID（主键）
    /// </summary>
    [Key]
    [Column("id")]
    public long Id { get; set; }

    /// <summary>
    /// 签名Token（唯一索引）
    /// </summary>
    [Required]
    [MaxLength(128)]
    [Column("signature_token")]
    public string SignatureToken { get; set; } = string.Empty;

    /// <summary>
    /// 调用方服务名称
    /// </summary>
    [Required]
    [MaxLength(100)]
    [Column("caller_service")]
    public string CallerService { get; set; } = string.Empty;

    /// <summary>
    /// 调用方服务ID（可选）
    /// </summary>
    [MaxLength(100)]
    [Column("caller_service_id")]
    public string? CallerServiceId { get; set; }

    /// <summary>
    /// 允许的操作类型（upload, download, delete, list等）
    /// </summary>
    [Required]
    [MaxLength(50)]
    [Column("allowed_operation")]
    public string AllowedOperation { get; set; } = string.Empty;

    /// <summary>
    /// 允许的文件类型（多个用逗号分隔，如 image,document,video）
    /// </summary>
    [MaxLength(200)]
    [Column("allowed_file_types")]
    public string? AllowedFileTypes { get; set; }

    /// <summary>
    /// 最大文件大小限制（字节）
    /// </summary>
    [Column("max_file_size")]
    public long? MaxFileSize { get; set; }

    /// <summary>
    /// 签名状态（active, expired, revoked）
    /// </summary>
    [Required]
    [MaxLength(20)]
    [Column("status")]
    public string Status { get; set; } = "active";

    /// <summary>
    /// 创建时间
    /// </summary>
    [Column("created_at")]
    public DateTime CreatedAt { get; set; }

    /// <summary>
    /// 过期时间
    /// </summary>
    [Column("expires_at")]
    public DateTime ExpiresAt { get; set; }

    /// <summary>
    /// 最后使用时间
    /// </summary>
    [Column("last_used_at")]
    public DateTime? LastUsedAt { get; set; }

    /// <summary>
    /// 使用次数
    /// </summary>
    [Column("usage_count")]
    public int UsageCount { get; set; } = 0;

    /// <summary>
    /// 最大使用次数（0表示无限制）
    /// </summary>
    [Column("max_usage_count")]
    public int MaxUsageCount { get; set; } = 0;

    /// <summary>
    /// 备注信息
    /// </summary>
    [MaxLength(500)]
    [Column("notes")]
    public string? Notes { get; set; }

    /// <summary>
    /// 撤销时间
    /// </summary>
    [Column("revoked_at")]
    public DateTime? RevokedAt { get; set; }

    /// <summary>
    /// 撤销原因
    /// </summary>
    [MaxLength(200)]
    [Column("revoke_reason")]
    public string? RevokeReason { get; set; }

    /// <summary>
    /// 创建者IP地址
    /// </summary>
    [MaxLength(50)]
    [Column("creator_ip")]
    public string? CreatorIp { get; set; }

    /// <summary>
    /// 检查签名是否有效
    /// </summary>
    public bool IsValid()
    {
        if (Status != "active")
            return false;

        if (ExpiresAt <= DateTime.UtcNow)
            return false;

        if (MaxUsageCount > 0 && UsageCount >= MaxUsageCount)
            return false;

        return true;
    }

    /// <summary>
    /// 检查是否允许指定的操作
    /// </summary>
    public bool IsOperationAllowed(string operation)
    {
        return AllowedOperation.Equals(operation, StringComparison.OrdinalIgnoreCase) ||
               AllowedOperation.Equals("*", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// 检查是否允许指定的文件类型
    /// </summary>
    public bool IsFileTypeAllowed(string fileType)
    {
        if (string.IsNullOrEmpty(AllowedFileTypes))
            return true;

        var allowedTypes = AllowedFileTypes.Split(',', StringSplitOptions.RemoveEmptyEntries);
        return allowedTypes.Any(t => t.Trim().Equals(fileType, StringComparison.OrdinalIgnoreCase) ||
                                     t.Trim().Equals("*", StringComparison.OrdinalIgnoreCase));
    }
}

/// <summary>
/// 签名状态枚举
/// </summary>
public static class SignatureStatus
{
    public const string Active = "active";
    public const string Expired = "expired";
    public const string Revoked = "revoked";
}

/// <summary>
/// 允许的操作类型
/// </summary>
public static class SignatureOperation
{
    public const string Upload = "upload";
    public const string Download = "download";
    public const string Delete = "delete";
    public const string List = "list";
    public const string All = "*";
}
