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
    /// 根据文件类型获取存储folder
    /// </summary>
    string GetFolderByFileType(string fileType);
    
    /// <summary>
    /// 根据文件类型获取bucket名称
    /// </summary>
    string GetBucketByFileType(string fileType);
    
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
}
