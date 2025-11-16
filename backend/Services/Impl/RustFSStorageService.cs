using Amazon.S3;
using Amazon.S3.Model;
using FileService.Config;
using FileService.Services.Interfaces;
using Microsoft.Extensions.Options;

namespace FileService.Services.Impl;

/// <summary>
/// RustFS 存储服务实现
/// </summary>
public class RustFSStorageService : IStorageService
{
    private readonly IAmazonS3 _s3Client;
    private readonly RustFSConfig _config;
    private readonly ILogger<RustFSStorageService> _logger;

    public RustFSStorageService(
        IAmazonS3 s3Client,
        IOptions<RustFSConfig> config,
        ILogger<RustFSStorageService> logger)
    {
        _s3Client = s3Client;
        _config = config.Value;
        _logger = logger;
    }

    public async Task<string> UploadFileAsync(string bucketName, string key, Stream fileStream, string contentType)
    {
        try
        {
            var request = new PutObjectRequest
            {
                BucketName = bucketName,
                Key = key,
                InputStream = fileStream,
                ContentType = contentType
            };

            var response = await _s3Client.PutObjectAsync(request);
            
            _logger.LogInformation($"文件上传成功: {bucketName}/{key}");
            return key;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, $"文件上传失败: {bucketName}/{key}");
            throw;
        }
    }

    public async Task<bool> DeleteFileAsync(string bucketName, string key)
    {
        try
        {
            var request = new DeleteObjectRequest
            {
                BucketName = bucketName,
                Key = key
            };

            await _s3Client.DeleteObjectAsync(request);
            _logger.LogInformation($"文件删除成功: {bucketName}/{key}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, $"文件删除失败: {bucketName}/{key}");
            return false;
        }
    }

    public async Task<bool> FileExistsAsync(string bucketName, string key)
    {
        try
        {
            var request = new GetObjectMetadataRequest
            {
                BucketName = bucketName,
                Key = key
            };

            await _s3Client.GetObjectMetadataAsync(request);
            return true;
        }
        catch (AmazonS3Exception ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return false;
        }
    }

    public async Task<GetObjectMetadataResponse?> GetFileMetadataAsync(string bucketName, string key)
    {
        try
        {
            var request = new GetObjectMetadataRequest
            {
                BucketName = bucketName,
                Key = key
            };

            return await _s3Client.GetObjectMetadataAsync(request);
        }
        catch (AmazonS3Exception ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<List<S3Object>> ListObjectsAsync(string bucketName, string? prefix = null)
    {
        var allObjects = new List<S3Object>();
        string? continuationToken = null;

        do
        {
            var request = new ListObjectsV2Request
            {
                BucketName = bucketName,
                Prefix = prefix,
                ContinuationToken = continuationToken
            };

            var response = await _s3Client.ListObjectsV2Async(request);
            allObjects.AddRange(response.S3Objects);

            continuationToken = response.IsTruncated ? response.NextContinuationToken : null;
        }
        while (continuationToken != null);

        return allObjects;
    }

    public async Task<bool> CreateBucketAsync(string bucketName)
    {
        try
        {
            var request = new PutBucketRequest
            {
                BucketName = bucketName
            };

            await _s3Client.PutBucketAsync(request);
            _logger.LogInformation($"Bucket 创建成功: {bucketName}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, $"Bucket 创建失败: {bucketName}");
            return false;
        }
    }

    public string GetFolderByFileType(string fileType)
    {
        return fileType.ToLower() switch
        {
            var type when type.StartsWith("image/") => _config.ImageFolder,
            var type when type.StartsWith("video/") => _config.VideoFolder,
            var type when type.StartsWith("audio/") => _config.AudioFolder,
            var type when type.Contains("pdf") || type.Contains("document") || type.Contains("text") => _config.DocumentFolder,
            var type when type.Contains("zip") || type.Contains("rar") || type.Contains("7z") || type.Contains("tar") => _config.ArchiveFolder,
            _ => string.Empty
        };
    }

    public string GetBucketByFileType(string fileType)
    {
        // 目前所有类型使用同一个bucket，未来可以根据类型分配不同bucket
        return _config.Bucket;
    }

    public string GetProxyUrl(string key, string bucket)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{bucket}/{key}";
        }
        return $"{bucket}/{key}";
    }

    public string GetProxyUrlByFileType(string key, string fileType, string bucket)
    {
        // key已经包含了文件夹路径，根据文件类型选择对应的代理路径
        return fileType.ToLower() switch
        {
            var type when type.StartsWith("image/") => GetProxyUrl(key, bucket),
            var type when type.StartsWith("video/") => GetProxyUrl(key, bucket),
            var type when type.StartsWith("audio/") => GetProxyUrl(key, bucket),
            var type when type.Contains("pdf") || type.Contains("document") || type.Contains("text") => GetProxyUrl(key, bucket),
            var type when type.Contains("zip") || type.Contains("rar") || type.Contains("7z") || type.Contains("tar") => GetProxyUrl(key, bucket),
            _ => GetProxyUrl(key, bucket) // 默认使用通用路径
        };
    }

    public (string Policy, string Signature) GenerateUploadSignature(
        string key, 
        string contentType, 
        string bucketName, 
        int expiresInMinutes = 60, 
        long maxSizeBytes = 10485760)
    {
        // 生成 S3 POST Policy 签名
        var expiration = DateTime.UtcNow.AddMinutes(expiresInMinutes).ToString("yyyy-MM-ddTHH:mm:ssZ");
        var region = _config.Region ?? "us-east-1";
        var dateString = DateTime.UtcNow.ToString("yyyyMMdd");
        var credential = $"{_config.AccessKey}/{dateString}/{region}/s3/aws4_request";

        // 构建 Policy
        var policy = $@"{{
            ""expiration"": ""{expiration}"",
            ""conditions"": [
                {{""bucket"": ""{bucketName}""}},
                {{""key"": ""{key}""}},
                {{""Content-Type"": ""{contentType}""}},
                [""content-length-range"", 0, {maxSizeBytes}],
                {{""x-amz-algorithm"": ""AWS4-HMAC-SHA256""}},
                {{""x-amz-credential"": ""{credential}""}},
                {{""x-amz-date"": ""{DateTime.UtcNow:yyyyMMddTHHmmssZ}""}}
            ]
        }}";

        // Base64 编码 Policy
        var policyBytes = System.Text.Encoding.UTF8.GetBytes(policy);
        var policyBase64 = Convert.ToBase64String(policyBytes);

        // 生成签名
        var dateKey = HmacSha256($"AWS4{_config.SecretKey}", dateString);
        var dateRegionKey = HmacSha256(dateKey, region);
        var dateRegionServiceKey = HmacSha256(dateRegionKey, "s3");
        var signingKey = HmacSha256(dateRegionServiceKey, "aws4_request");
        var signature = HmacSha256Hex(signingKey, policyBase64);

        return (policyBase64, signature);
    }

    public string GetUploadUrl(string bucketName)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath) && _config.ProxyPath.Contains("/api/files"))
        {
            // 使用代理路径（去掉 /api/files，改为 /upload）
            var baseUrl = _config.ProxyPath.Replace("/api/files", "/upload");
            return $"{baseUrl}/{bucketName}";
        }
        else
        {
            // 开发环境或无代理：直接使用存储端点
            return $"{(_config.UseHttps ? "https" : "http")}://{_config.Endpoint}/{bucketName}";
        }
    }

    // Helper methods for HMAC-SHA256
    private byte[] HmacSha256(string key, string data)
    {
        var keyBytes = System.Text.Encoding.UTF8.GetBytes(key);
        return HmacSha256(keyBytes, data);
    }

    private byte[] HmacSha256(byte[] key, string data)
    {
        using var hmac = new System.Security.Cryptography.HMACSHA256(key);
        var dataBytes = System.Text.Encoding.UTF8.GetBytes(data);
        return hmac.ComputeHash(dataBytes);
    }

    private string HmacSha256Hex(byte[] key, string data)
    {
        var hash = HmacSha256(key, data);
        return BitConverter.ToString(hash).Replace("-", "").ToLower();
    }
}
