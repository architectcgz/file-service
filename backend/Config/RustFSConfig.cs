namespace FileService.Config;

/// <summary>
/// RustFS 存储服务配置
/// 注意：Bucket和Folder参数已移除，应由调用方在请求中提供
/// </summary>
public class RustFSConfig
{
    public string AccessKey { get; set; } = string.Empty;
    public string SecretKey { get; set; } = string.Empty;
    public string Endpoint { get; set; } = string.Empty;
    public string Region { get; set; } = "us-east-1";
    public bool UseHttps { get; set; } = true;
    public bool ForcePathStyle { get; set; } = true;
    
    /// <summary>
    /// 统一代理路径配置
    /// 用于生成文件访问URL，例如：https://www.archi0v0.top/api/files
    /// </summary>
    public string ProxyPath { get; set; } = string.Empty;
}

