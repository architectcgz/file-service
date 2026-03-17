namespace FileService.Config;

/// <summary>
/// 文件服务安全配置
/// </summary>
public class FileServiceSecurityConfig
{
    /// <summary>
    /// [已废弃] 允许的调用方共享密钥（已被签名管理系统取代）
    /// </summary>
    [Obsolete("共享密钥验证已废弃，请使用签名管理系统（SignatureToken）进行认证")]
    public string? SharedSecret { get; set; }

    /// <summary>
    /// [已废弃] 是否启用共享密钥验证（已被签名管理系统取代）
    /// </summary>
    [Obsolete("共享密钥验证已废弃，请使用签名管理系统（SignatureToken）进行认证")]
    public bool EnableSharedSecretValidation { get; set; } = true;

    /// <summary>
    /// 允许的调用方 IP 地址列表（可选，用于额外验证）
    /// </summary>
    public List<string> AllowedIpAddresses { get; set; } = new();

    /// <summary>
    /// 管理员API Key（用于管理员接口认证）
    /// </summary>
    public string? AdminApiKey { get; set; }

    /// <summary>
    /// 是否启用管理员API Key验证
    /// </summary>
    public bool EnableAdminApiKeyValidation { get; set; } = true;

    /// <summary>
    /// 管理员用户名（用于登录）
    /// </summary>
    public string? AdminUsername { get; set; }

    /// <summary>
    /// 管理员密码哈希（SHA256）
    /// </summary>
    public string? AdminPasswordHash { get; set; }

    /// <summary>
    /// 管理员Token（用于API调用）
    /// </summary>
    public string? AdminToken { get; set; }

    /// <summary>
    /// Session过期时间（分钟），默认30分钟
    /// </summary>
    public int AdminSessionTimeoutMinutes { get; set; } = 30;
}

