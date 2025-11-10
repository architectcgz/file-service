namespace FileService.Config;

public class RustFSConfig
{
    public string AccessKey { get; set; } = string.Empty;
    public string SecretKey { get; set; } = string.Empty;
    public string Endpoint { get; set; } = string.Empty;
    public string Region { get; set; } = "us-east-1";
    public bool UseHttps { get; set; } = true;
    public bool ForcePathStyle { get; set; } = true;
    
    // 统一存储桶配置
    public string Bucket { get; set; } = "blog-files";  // 统一存储桶
    
    // 各种文件类型的子目录路径
    public string ImageFolder { get; set; } = "images";      // 图片子目录
    public string DocumentFolder { get; set; } = "documents"; // 文档子目录
    public string VideoFolder { get; set; } = "videos";      // 视频子目录
    public string AudioFolder { get; set; } = "audios";      // 音频子目录
    public string ArchiveFolder { get; set; } = "archives";  // 压缩包子目录
    
    // 统一代理路径配置
    public string ProxyPath { get; set; } = string.Empty;  // 统一代理路径，如：http://www.archi0v0.top/api/files
}

