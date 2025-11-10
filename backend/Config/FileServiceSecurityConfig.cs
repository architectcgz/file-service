namespace FileService.Config;

/// <summary>
/// 文件服务安全配置
/// </summary>
public class FileServiceSecurityConfig
{
    /// <summary>
    /// 允许的调用方共享密钥（用于验证请求来源）
    /// </summary>
    public string? SharedSecret { get; set; }

    /// <summary>
    /// 是否启用共享密钥验证
    /// </summary>
    public bool EnableSharedSecretValidation { get; set; } = true;

    /// <summary>
    /// 允许的调用方 IP 地址列表（可选，用于额外验证）
    /// </summary>
    public List<string> AllowedIpAddresses { get; set; } = new();
}

