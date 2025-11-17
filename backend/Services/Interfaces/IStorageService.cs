using Amazon.S3.Model;

namespace FileService.Services.Interfaces;

/// <summary>
/// 存储服务接口 - 抽象底层存储实现（RustFS、MinIO、OSS等）
/// </summary>
public interface IStorageService
{
    /// <summary>
    /// 上传文件
    /// </summary>
    Task<string> UploadFileAsync(string bucketName, string key, Stream fileStream, string contentType);
    
    /// <summary>
    /// 删除文件
    /// </summary>
    Task<bool> DeleteFileAsync(string bucketName, string key);
    
    /// <summary>
    /// 检查文件是否存在
    /// </summary>
    Task<bool> FileExistsAsync(string bucketName, string key);
    
    /// <summary>
    /// 获取文件元数据
    /// </summary>
    Task<GetObjectMetadataResponse?> GetFileMetadataAsync(string bucketName, string key);
    
    /// <summary>
    /// 列举bucket中的所有对象
    /// </summary>
    Task<List<S3Object>> ListObjectsAsync(string bucketName, string? prefix = null);
    
    /// <summary>
    /// 创建bucket
    /// </summary>
    Task<bool> CreateBucketAsync(string bucketName);
    
    
    /// <summary>
    /// 生成代理访问URL
    /// </summary>
    string GetProxyUrl(string key, string bucket);
    
    /// <summary>
    /// 根据文件类型生成对应的代理URL
    /// </summary>
    string GetProxyUrlByFileType(string key, string fileType, string bucket);
    
    /// <summary>
    /// 生成直传签名（用于前端直传到存储服务）
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="contentType">文件类型</param>
    /// <param name="bucketName">Bucket名称</param>
    /// <param name="expiresInMinutes">签名过期时间（分钟）</param>
    /// <param name="maxSizeBytes">最大文件大小</param>
    /// <returns>签名信息</returns>
    (string Policy, string Signature) GenerateUploadSignature(
        string key, 
        string contentType, 
        string bucketName, 
        int expiresInMinutes = 60, 
        long maxSizeBytes = 10485760);
    
    /// <summary>
    /// 获取上传URL
    /// </summary>
    string GetUploadUrl(string bucketName);
    
    /// <summary>
    /// 上传流到存储服务
    /// </summary>
    Task<bool> UploadStreamAsync(Stream stream, string fileName, string bucket);
    
    /// <summary>
    /// 生成预签名URL（指定bucket，支持下载模式）
    /// </summary>
    string GetPresignedUrl(string bucketName, string key, bool isDownload = false, int expiresInMinutes = 60);
    
    /// <summary>
    /// 清除预签名URL缓存
    /// </summary>
    void ClearPresignedUrlCache(string key, int? expiresInMinutes = null);
    
    /// <summary>
    /// 列出所有bucket
    /// </summary>
    Task<List<string>> ListBucketsAsync();
    
    /// <summary>
    /// 列出bucket中的文件夹和根目录文件
    /// </summary>
    Task<BucketContentsResult> ListFoldersAndFilesAsync(string bucketName);
    
    /// <summary>
    /// 列出指定文件夹下的文件（分页）
    /// </summary>
    Task<S3FilePageResult> ListFilesInFolderAsync(string bucketName, string folder, int maxKeys = 20, string? continuationToken = null);
    
    /// <summary>
    /// 删除bucket
    /// </summary>
    Task<bool> DeleteBucketAsync(string bucketName);
    
    /// <summary>
    /// 检查bucket是否存在
    /// </summary>
    Task<bool> BucketExistsAsync(string bucketName);
    
    /// <summary>
    /// 创建目录
    /// </summary>
    Task<bool> CreateDirectoryAsync(string bucketName, string directoryPath);
    
    /// <summary>
    /// 检查目录是否存在
    /// </summary>
    Task<bool> DirectoryExistsAsync(string bucketName, string directoryPath);
    
    /// <summary>
    /// 获取bucket中的文件总数
    /// </summary>
    Task<long> GetBucketFileCountAsync(string bucketName);
    
    /// <summary>
    /// 修复文件的ContentType元数据
    /// </summary>
    Task<bool> FixFileContentTypeAsync(string bucketName, string key);
}

/// <summary>
/// S3 对象信息
/// </summary>
public class S3ObjectInfo
{
    public string Key { get; set; } = string.Empty;
    public long Size { get; set; }
    public DateTime LastModified { get; set; }
    public string? ETag { get; set; }
    public string? Url { get; set; }
    public string? DownloadUrl { get; set; }
}

/// <summary>
/// S3 文件分页结果
/// </summary>
public class S3FilePageResult
{
    public List<S3ObjectInfo> Files { get; set; } = new();
    public bool IsTruncated { get; set; }
    public string? NextContinuationToken { get; set; }
    public int KeyCount { get; set; }
}

/// <summary>
/// 存储桶内容结果（包含文件夹和文件）
/// </summary>
public class BucketContentsResult
{
    public List<string> Folders { get; set; } = new();
    public List<S3ObjectInfo> Files { get; set; } = new();
}
