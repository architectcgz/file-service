using Amazon.S3;
using Amazon.S3.Model;
using Amazon.S3.Transfer;
using FileService.Config;
using FileService.Exceptions;
using FileService.Constants;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace FileService.Utils;

public class RustFSUtil
{
    private readonly RustFSConfig _config;
    private readonly IAmazonS3 _s3Client;
    private readonly ILogger _logger;
    private readonly IMemoryCache _memoryCache;

    public RustFSUtil(IConfiguration configuration, ILogger<RustFSUtil> logger, IMemoryCache memoryCache)
    {
        _logger = logger;
        _memoryCache = memoryCache;
        _config = new RustFSConfig();
        
        var rustFSSection = configuration.GetSection("RustFSConfig");
        if (!rustFSSection.Exists())
        {
            throw new InvalidOperationException("配置节 'RustFSConfig' 未找到。");
        }
        
        rustFSSection.Bind(_config);

        // 调试日志：检查配置绑定
        _logger.LogInformation($"RustFS配置加载 - Bucket: {_config.Bucket}");
        _logger.LogInformation($"RustFS配置加载 - ImageFolder: {_config.ImageFolder}");
        _logger.LogInformation($"RustFS配置加载 - DocumentFolder: {_config.DocumentFolder}");
        _logger.LogInformation($"RustFS配置加载 - ProxyPath: {_config.ProxyPath}");
        _logger.LogInformation($"RustFS配置加载 - Endpoint: {_config.Endpoint}");
        _logger.LogInformation($"当前环境: {Environment.GetEnvironmentVariable("ASPNETCORE_ENVIRONMENT") ?? "未设置"}");

        if (string.IsNullOrEmpty(_config.AccessKey))
        {
            throw new InvalidOperationException("AccessKey 未配置");
        }

        if (string.IsNullOrEmpty(_config.SecretKey))
        {
            throw new InvalidOperationException("SecretKey 未配置");
        }

        if (string.IsNullOrEmpty(_config.Bucket))
        {
            throw new InvalidOperationException("Bucket 未配置");
        }

        if (string.IsNullOrEmpty(_config.Endpoint))
        {
            throw new InvalidOperationException("Endpoint 未配置");
        }
        
        // 创建S3客户端
        var serviceUrl = $"{(_config.UseHttps ? "https" : "http")}://{_config.Endpoint}";

        
        var s3Config = new AmazonS3Config
        {
            ForcePathStyle = _config.ForcePathStyle,
            UseHttp = !_config.UseHttps,
            ServiceURL = serviceUrl,
        };
        _logger.LogInformation($"构建的ServiceURL: {serviceUrl}");
        
        _logger.LogInformation($"accessKey:{_config.AccessKey},secretKey:{_config.SecretKey},bucket:{_config.Bucket},serviceUrl:{s3Config.ServiceURL}");
        
        _s3Client = new AmazonS3Client(_config.AccessKey, _config.SecretKey, s3Config);
    }

    /// <summary>
    /// 上传本地文件到RustFS
    /// </summary>
    /// <param name="localFilePath">本地文件路径</param>
    /// <returns>上传成功返回文件的URL，失败返回null</returns>
    public async Task<string?> UploadFileAsync(string localFilePath)
    {
        if (!File.Exists(localFilePath))
        {
            _logger.LogInformation("本地文件不存在：" + localFilePath);
            return null;
        }

        try
        {
            // 生成文件Key（使用文件名）
            var key = Path.GetFileName(localFilePath);
            _logger.LogInformation("上传的key: " + key);

            // 使用TransferUtility进行上传
            using var transferUtility = new TransferUtility(_s3Client);
            await transferUtility.UploadAsync(localFilePath, _config.Bucket, key);

            // 构建文件URL
            var fileUrl = $"{(_config.UseHttps ? "https" : "http")}://{_config.Endpoint}/{_config.Bucket}/{key}";
            _logger.LogInformation("上传成功，文件URL: " + fileUrl);
            
            return fileUrl;
        }
        catch (Exception ex)
        {
            _logger.LogError("上传异常：" + ex.Message);
            _logger.LogError("异常详情：" + ex);
            throw; // 保留原始异常信息
        }
    }

    /// <summary>
    /// 上传流数据到RustFS（使用默认桶）
    /// </summary>
    /// <param name="stream">文件流</param>
    /// <param name="fileName">文件名</param>
    /// <returns>上传成功返回true，失败抛出异常</returns>
    public async Task<bool> UploadStreamAsync(Stream stream, string fileName)
    {
        return await UploadStreamAsync(stream, fileName, _config.Bucket);
    }

    /// <summary>
    /// 上传流数据到RustFS（指定存储桶）
    /// </summary>
    /// <param name="stream">文件流</param>
    /// <param name="fileName">文件名</param>
    /// <param name="bucket">存储桶名称</param>
    /// <returns>上传成功返回true，失败抛出异常</returns>
    public async Task<bool> UploadStreamAsync(Stream stream, string fileName, string bucket)
    {
        try
        {
            // 生成文件Key
            var key = fileName;
            _logger.LogInformation($"上传的key: {key}, 存储桶: {bucket}");

            // 使用TransferUtility进行上传
            using var transferUtility = new TransferUtility(_s3Client);
            await transferUtility.UploadAsync(stream, bucket, key);

            _logger.LogInformation($"上传成功，文件Key: {key}, 存储桶: {bucket}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"上传异常：{ex.Message}");
            _logger.LogError($"异常详情：{ex}");
            throw; // 保留原始异常信息
        }
    }

    /// <summary>
    /// 删除文件
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <returns>删除是否成功</returns>
    public async Task<bool> DeleteFileAsync(string key)
    {
        try
        {
            var deleteRequest = new DeleteObjectRequest
            {
                BucketName = _config.Bucket,
                Key = key
            };

            await _s3Client.DeleteObjectAsync(deleteRequest);
            _logger.LogInformation("文件删除成功: " + key);
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError("删除文件异常：" + ex.Message);
            return false;
        }
    }

    /// <summary>
    /// 检查文件是否存在
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <returns>文件是否存在</returns>
    public async Task<bool> FileExistsAsync(string key)
    {
        try
        {
            var request = new GetObjectMetadataRequest
            {
                BucketName = _config.Bucket,
                Key = key
            };

            await _s3Client.GetObjectMetadataAsync(request);
            return true;
        }
        catch (AmazonS3Exception ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError("检查文件存在性异常：" + ex.Message);
            return false;
        }
    }

    /// <summary>
    /// 生成S3 POST策略签名（用于直传）
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="contentType">文件类型</param>
    /// <param name="expiresInMinutes">过期时间（分钟）</param>
    /// <param name="maxSizeBytes">最大文件大小（字节）</param>
    /// <returns>包含策略和签名的对象</returns>
    public (string Policy, string Signature) GeneratePostPolicySignature(string key, string contentType, int expiresInMinutes = 60, long maxSizeBytes = 10485760)
    {
        try
        {
            // 创建策略文档
            var policy = new
            {
                expiration = DateTime.UtcNow.AddMinutes(expiresInMinutes).ToString("yyyy-MM-ddTHH:mm:ss.fffZ"),
                conditions = new object[]
                {
                    new { bucket = _config.Bucket },
                    new { key = key },
                    new Dictionary<string, string> { { "Content-Type", contentType } },
                    new Dictionary<string, string> { { "x-amz-algorithm", "AWS4-HMAC-SHA256" } },
                    new Dictionary<string, string> { { "x-amz-credential", $"{_config.AccessKey}/{DateTime.UtcNow:yyyyMMdd}/{_config.Region}/s3/aws4_request" } },
                    new Dictionary<string, string> { { "x-amz-date", DateTime.UtcNow.ToString("yyyyMMddTHHmmssZ") } },
                    new object[] { "content-length-range", 0, maxSizeBytes } // 自定义最大文件大小
                }
            };

            // 将策略转换为JSON并Base64编码
            var policyJson = System.Text.Json.JsonSerializer.Serialize(policy);
            var policyBase64 = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(policyJson));

            // 生成签名
            var dateKey = HmacSha256Bytes($"AWS4{_config.SecretKey}", DateTime.UtcNow.ToString("yyyyMMdd"));
            var dateRegionKey = HmacSha256Bytes(dateKey, _config.Region);
            var dateRegionServiceKey = HmacSha256Bytes(dateRegionKey, "s3");
            var signingKey = HmacSha256Bytes(dateRegionServiceKey, "aws4_request");
            var signature = HmacSha256String(signingKey, policyBase64);

            _logger.LogInformation($"生成POST策略签名 - Key: {key}, Policy: {policyBase64}");
            
            return (policyBase64, signature);
        }
        catch (Exception ex)
        {
            _logger.LogError("生成POST策略签名异常：" + ex.Message);
            throw new BusinessException(BusinessError.UploadError);
        }
    }

    /// <summary>
    /// HMAC-SHA256签名（返回字符串）
    /// </summary>
    private string HmacSha256String(byte[] key, string data)
    {
        using var hmac = new System.Security.Cryptography.HMACSHA256(key);
        var hash = hmac.ComputeHash(System.Text.Encoding.UTF8.GetBytes(data));
        return Convert.ToHexString(hash).ToLower();
    }

    /// <summary>
    /// HMAC-SHA256签名（字符串密钥版本，返回字节数组）
    /// </summary>
    private byte[] HmacSha256Bytes(string key, string data)
    {
        using var hmac = new System.Security.Cryptography.HMACSHA256(System.Text.Encoding.UTF8.GetBytes(key));
        return hmac.ComputeHash(System.Text.Encoding.UTF8.GetBytes(data));
    }

    /// <summary>
    /// HMAC-SHA256签名（字节数组密钥版本，返回字节数组）
    /// </summary>
    private byte[] HmacSha256Bytes(byte[] key, string data)
    {
        using var hmac = new System.Security.Cryptography.HMACSHA256(key);
        return hmac.ComputeHash(System.Text.Encoding.UTF8.GetBytes(data));
    }

    /// <summary>
    /// 获取预签名URL（用于访问私有桶中的文件）
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="expiresInMinutes">过期时间（分钟）</param>
    /// <returns>预签名URL</returns>
    public string GetPresignedUrl(string key, int expiresInMinutes = 60)
    {
        try
        {
            // 构建缓存键
            var cacheKey = $"presigned_url:{key}:{expiresInMinutes}";
            
            // 尝试从内存缓存获取
            if (_memoryCache.TryGetValue(cacheKey, out string? cachedUrl) && !string.IsNullOrEmpty(cachedUrl))
            {
                _logger.LogInformation($"从内存缓存获取预签名URL: {key}");
                return cachedUrl;
            }

            var request = new GetPreSignedUrlRequest
            {
                BucketName = _config.Bucket,
                Key = key,
                Verb = HttpVerb.GET, // 改为GET请求，用于访问文件
                Expires = DateTime.UtcNow.AddMinutes(expiresInMinutes)
            };

            var presignedUrl = _s3Client.GetPreSignedURL(request);
            _logger.LogInformation("生成预签名URL: " + presignedUrl);
            
            // 如果配置要求使用HTTP，但生成的URL是HTTPS，则强制替换为HTTP
            if (!_config.UseHttps && presignedUrl.StartsWith("https://"))
            {
                presignedUrl = presignedUrl.Replace("https://", "http://");
                _logger.LogInformation("强制转换为HTTP URL: " + presignedUrl);
            }
            
            // 缓存预签名URL，过期时间设置为比URL有效期短1分钟
            var cacheExpiration = TimeSpan.FromMinutes(expiresInMinutes - 1);
            _memoryCache.Set(cacheKey, presignedUrl, cacheExpiration);
            _logger.LogInformation($"预签名URL已缓存，键: {cacheKey}，过期时间: {cacheExpiration}");
            
            return presignedUrl;
        }
        catch (Exception ex)
        {
            _logger.LogError("生成预签名URL异常：" + ex.Message);
            throw new BusinessException(BusinessError.UploadError);
        }
    }

    /// <summary>
    /// 清除预签名URL缓存
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="expiresInMinutes">过期时间（分钟），如果为null则清除所有相关缓存</param>
    public void ClearPresignedUrlCache(string key, int? expiresInMinutes = null)
    {
        try
        {
            if (expiresInMinutes.HasValue)
            {
                // 清除特定过期时间的缓存
                var cacheKey = $"presigned_url:{key}:{expiresInMinutes.Value}";
                _memoryCache.Remove(cacheKey);
                _logger.LogInformation($"已清除预签名URL缓存: {cacheKey}");
            }
            else
            {
                // 清除该文件的所有缓存（通过模式匹配）
                // 注意：这里简化处理，实际项目中可以使用更精确的缓存键管理
                var cacheKey = $"presigned_url:{key}:";
                // 由于IMemoryCache不支持模式匹配，这里只是记录日志
                _logger.LogInformation($"请求清除文件的所有预签名URL缓存: {key}");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"清除预签名URL缓存异常: {ex.Message}");
        }
    }

    /// <summary>
    /// 根据文件类型获取对应的存储桶
    /// </summary>
    /// <param name="fileType">文件MIME类型</param>
    /// <returns>对应的存储桶名称</returns>
    public string GetBucketByFileType(string fileType)
    {
        // 现在所有文件都使用同一个存储桶
        return _config.Bucket;
    }
    
    /// <summary>
    /// 根据文件类型获取存储路径前缀（子目录）
    /// </summary>
    /// <param name="fileType">文件MIME类型</param>
    /// <returns>存储路径前缀</returns>
    public string GetFolderByFileType(string fileType)
    {
        var result = fileType.ToLower() switch
        {
            var type when type.StartsWith("image/") => _config.ImageFolder,
            var type when type.StartsWith("video/") => _config.VideoFolder,
            var type when type.StartsWith("audio/") => _config.AudioFolder,
            var type when type.Contains("pdf") || type.Contains("document") || type.Contains("text") => _config.DocumentFolder,
            var type when type.Contains("zip") || type.Contains("rar") || type.Contains("7z") || type.Contains("tar") => _config.ArchiveFolder,
            _ => _config.ImageFolder // 默认使用图片文件夹
        };
        
        _logger.LogInformation($"GetFolderByFileType - 文件类型: {fileType}, 返回文件夹: {result}");
        _logger.LogInformation($"配置检查 - ImageFolder: {_config.ImageFolder}, DocumentFolder: {_config.DocumentFolder}");
        
        return result;
    }

    /// <summary>
    /// 获取图片代理URL（用于前端显示）
    /// </summary>
    /// <param name="key">图片文件Key</param>
    /// <returns>图片代理URL</returns>
    public string GetProxyImageUrl(string key)
    {
        // 使用统一代理路径，拼接存储桶名和key
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{_config.Bucket}/{key}";
        }
        // 否则返回相对路径格式（现在key已经包含了子目录）
        return $"{_config.Bucket}/{key}";
    }

    /// <summary>
    /// 获取文档代理URL（用于前端显示）
    /// </summary>
    /// <param name="key">文档文件Key</param>
    /// <returns>文档代理URL</returns>
    public string GetProxyDocumentUrl(string key)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{_config.Bucket}/{key}";
        }
        return $"{_config.Bucket}/{key}";
    }

    /// <summary>
    /// 获取视频代理URL（用于前端显示）
    /// </summary>
    /// <param name="key">视频文件Key</param>
    /// <returns>视频代理URL</returns>
    public string GetProxyVideoUrl(string key)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{_config.Bucket}/{key}";
        }
        return $"{_config.Bucket}/{key}";
    }

    /// <summary>
    /// 获取音频代理URL（用于前端显示）
    /// </summary>
    /// <param name="key">音频文件Key</param>
    /// <returns>音频代理URL</returns>
    public string GetProxyAudioUrl(string key)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{_config.Bucket}/{key}";
        }
        return $"{_config.Bucket}/{key}";
    }

    /// <summary>
    /// 获取压缩包代理URL（用于前端显示）
    /// </summary>
    /// <param name="key">压缩包文件Key</param>
    /// <returns>压缩包代理URL</returns>
    public string GetProxyArchiveUrl(string key)
    {
        if (!string.IsNullOrEmpty(_config.ProxyPath))
        {
            return $"{_config.ProxyPath}/{_config.Bucket}/{key}";
        }
        return $"{_config.Bucket}/{key}";
    }

    /// <summary>
    /// 根据文件类型获取对应的代理URL
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="fileType">文件MIME类型</param>
    /// <returns>对应类型的代理URL</returns>
    public string GetProxyUrlByFileType(string key, string fileType)
    {
        // 现在key已经包含了文件夹路径，直接根据文件类型选择对应的代理路径
        return fileType.ToLower() switch
        {
            var type when type.StartsWith("image/") => GetProxyImageUrl(key),
            var type when type.StartsWith("video/") => GetProxyVideoUrl(key),
            var type when type.StartsWith("audio/") => GetProxyAudioUrl(key),
            var type when type.Contains("pdf") || type.Contains("document") || type.Contains("text") => GetProxyDocumentUrl(key),
            var type when type.Contains("zip") || type.Contains("rar") || type.Contains("7z") || type.Contains("tar") => GetProxyArchiveUrl(key),
            _ => GetProxyImageUrl(key) // 默认使用图片路径
        };
    }
}

