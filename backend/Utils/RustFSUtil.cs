using Amazon.S3;
using Amazon.S3.Model;
using Amazon.S3.Transfer;
using FileService.Config;
using FileService.Exceptions;
using FileService.Constants;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using System.Net;

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

            // 获取文件的 Content-Type
            var contentType = GetContentType(key);

            // 使用 PutObjectRequest 以便设置元数据
            var putRequest = new PutObjectRequest
            {
                BucketName = _config.Bucket,
                Key = key,
                FilePath = localFilePath,
                ContentType = contentType
            };

            await _s3Client.PutObjectAsync(putRequest);

            // 构建文件URL
            var fileUrl = $"{(_config.UseHttps ? "https" : "http")}://{_config.Endpoint}/{_config.Bucket}/{key}";
            _logger.LogInformation($"上传成功，文件URL: {fileUrl}, ContentType: {contentType}");
            
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

            // 获取文件的 Content-Type
            var contentType = GetContentType(fileName);
            
            // 使用 PutObjectRequest 以便设置元数据
            var putRequest = new PutObjectRequest
            {
                BucketName = bucket,
                Key = key,
                InputStream = stream,
                ContentType = contentType,
                // 不设置 ContentDisposition，让预签名URL控制
            };

            await _s3Client.PutObjectAsync(putRequest);

            _logger.LogInformation($"上传成功，文件Key: {key}, 存储桶: {bucket}, ContentType: {contentType}");
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
    /// 删除文件（使用默认存储桶）
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <returns>删除是否成功</returns>
    public async Task<bool> DeleteFileAsync(string key)
    {
        return await DeleteFileAsync(key, _config.Bucket);
    }

    /// <summary>
    /// 删除文件（指定存储桶）
    /// </summary>
    /// <param name="key">文件Key</param>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>删除是否成功</returns>
    public async Task<bool> DeleteFileAsync(string key, string bucketName)
    {
        try
        {
            var deleteRequest = new DeleteObjectRequest
            {
                BucketName = bucketName,
                Key = key
            };

            await _s3Client.DeleteObjectAsync(deleteRequest);
            _logger.LogInformation($"文件删除成功: {key}, 存储桶: {bucketName}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"删除文件异常：{ex.Message}, 存储桶: {bucketName}");
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

    /// <summary>
    /// 创建存储桶
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>创建是否成功</returns>
    public async Task<bool> CreateBucketAsync(string bucketName)
    {
        try
        {
            // 检查存储桶是否已存在
            var bucketExists = await Amazon.S3.Util.AmazonS3Util.DoesS3BucketExistV2Async(_s3Client, bucketName);
            if (bucketExists)
            {
                _logger.LogInformation($"存储桶已存在: {bucketName}");
                return true;
            }

            // 创建存储桶
            var request = new PutBucketRequest
            {
                BucketName = bucketName
            };

            await _s3Client.PutBucketAsync(request);
            _logger.LogInformation($"存储桶创建成功: {bucketName}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"创建存储桶异常：{ex.Message}, 存储桶: {bucketName}");
            return false;
        }
    }

    /// <summary>
    /// 删除存储桶
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>删除是否成功</returns>
    public async Task<bool> DeleteBucketAsync(string bucketName)
    {
        try
        {
            // 检查存储桶是否存在
            var bucketExists = await Amazon.S3.Util.AmazonS3Util.DoesS3BucketExistV2Async(_s3Client, bucketName);
            if (!bucketExists)
            {
                _logger.LogInformation($"存储桶不存在: {bucketName}");
                return false;
            }

            // 删除存储桶
            var request = new DeleteBucketRequest
            {
                BucketName = bucketName
            };

            await _s3Client.DeleteBucketAsync(request);
            _logger.LogInformation($"存储桶删除成功: {bucketName}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"删除存储桶异常：{ex.Message}, 存储桶: {bucketName}");
            return false;
        }
    }

    /// <summary>
    /// 检查存储桶是否存在
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>存储桶是否存在</returns>
    public async Task<bool> BucketExistsAsync(string bucketName)
    {
        try
        {
            return await Amazon.S3.Util.AmazonS3Util.DoesS3BucketExistV2Async(_s3Client, bucketName);
        }
        catch (Exception ex)
        {
            _logger.LogError($"检查存储桶存在性异常：{ex.Message}, 存储桶: {bucketName}");
            return false;
        }
    }

    /// <summary>
    /// 列出所有存储桶
    /// </summary>
    /// <returns>存储桶名称列表</returns>
    public async Task<List<string>> ListBucketsAsync()
    {
        try
        {
            var response = await _s3Client.ListBucketsAsync();
            return response.Buckets.Select(b => b.BucketName).ToList();
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出存储桶异常：{ex.Message}");
            return new List<string>();
        }
    }
    
    /// <summary>
    /// 列出指定存储桶中的所有文件夹（顶级前缀）
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>文件夹名称列表</returns>
    public async Task<List<string>> ListFoldersAsync(string bucketName)
    {
        try
        {
            var request = new ListObjectsV2Request
            {
                BucketName = bucketName,
                Delimiter = "/" // 使用分隔符来获取文件夹（前缀）
            };
            
            var response = await _s3Client.ListObjectsV2Async(request);
            
            // CommonPrefixes 包含所有文件夹（以 / 结尾的前缀）
            var folders = response.CommonPrefixes
                .Select(prefix => prefix.TrimEnd('/'))
                .Where(folder => !string.IsNullOrEmpty(folder))
                .ToList();
            
            _logger.LogInformation($"从存储桶 '{bucketName}' 列出 {folders.Count} 个文件夹");
            return folders;
        }
        catch (AmazonS3Exception ex)
        {
            _logger.LogError($"列出存储桶 '{bucketName}' 的文件夹失败：{ex.Message}");
            return new List<string>();
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出文件夹异常：{ex.Message}");
            return new List<string>();
        }
    }
    
    /// <summary>
    /// 列出指定文件夹下的文件信息（支持分页）
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <param name="folder">文件夹路径（不含前后斜杠）</param>
    /// <param name="maxKeys">每页最大文件数，默认20</param>
    /// <param name="continuationToken">分页令牌，用于获取下一页</param>
    /// <returns>文件信息分页结果</returns>
    public async Task<S3FilePageResult> ListFilesInFolderAsync(string bucketName, string folder, int maxKeys = 20, string? continuationToken = null)
    {
        try
        {
            var prefix = string.IsNullOrEmpty(folder) ? "" : folder.TrimEnd('/') + "/";
            var request = new ListObjectsV2Request
            {
                BucketName = bucketName,
                Prefix = prefix,
                Delimiter = "/", // 只获取当前层级的文件
                MaxKeys = maxKeys,
                ContinuationToken = continuationToken
            };
            
            var response = await _s3Client.ListObjectsV2Async(request);
            
            var files = response.S3Objects
                .Where(obj => !obj.Key.EndsWith("/")) // 排除文件夹本身
                .Select(obj => new S3ObjectInfo
                {
                    Key = obj.Key,
                    Size = obj.Size,
                    LastModified = obj.LastModified,
                    ETag = obj.ETag?.Trim('"'),
                    Url = GetPresignedUrl(bucketName, obj.Key, isDownload: false),
                    DownloadUrl = GetPresignedUrl(bucketName, obj.Key, isDownload: true)
                })
                .ToList();
            
            var result = new S3FilePageResult
            {
                Files = files,
                IsTruncated = response.IsTruncated,
                NextContinuationToken = response.NextContinuationToken,
                KeyCount = response.KeyCount
            };
            
            _logger.LogInformation($"从存储桶 '{bucketName}' 的文件夹 '{folder}' 列出 {files.Count} 个文件，是否有更多: {response.IsTruncated}");
            return result;
        }
        catch (AmazonS3Exception ex)
        {
            _logger.LogError($"列出存储桶 '{bucketName}' 文件夹 '{folder}' 的文件失败：{ex.Message}");
            return new S3FilePageResult { Files = new List<S3ObjectInfo>() };
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出文件夹文件异常：{ex.Message}");
            return new S3FilePageResult { Files = new List<S3ObjectInfo>() };
        }
    }
    
    /// <summary>
    /// 生成预签名URL用于文件访问
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <param name="key">文件key</param>
    /// <param name="isDownload">是否为下载模式（true=下载，false=预览）</param>
    /// <param name="expiresInMinutes">过期时间（分钟），默认60分钟</param>
    /// <returns>预签名URL</returns>
    public string GetPresignedUrl(string bucketName, string key, bool isDownload = false, int expiresInMinutes = 60)
    {
        try
        {
            var request = new GetPreSignedUrlRequest
            {
                BucketName = bucketName,
                Key = key,
                Expires = DateTime.UtcNow.AddMinutes(expiresInMinutes),
                Protocol = _config.UseHttps ? Protocol.HTTPS : Protocol.HTTP
            };
            
            // 获取文件MIME类型
            var contentType = GetContentType(key);
            
            // 设置响应头
            if (isDownload)
            {
                // 下载模式：强制下载
                var fileName = Path.GetFileName(key);
                // 使用 UTF-8 编码处理文件名，支持中文等特殊字符
                var encodedFileName = WebUtility.UrlEncode(fileName);
                request.ResponseHeaderOverrides.ContentDisposition = $"attachment; filename*=UTF-8''{encodedFileName}";
                request.ResponseHeaderOverrides.ContentType = contentType;
            }
            else
            {
                // 预览模式：在浏览器中显示
                request.ResponseHeaderOverrides.ContentDisposition = "inline";
                request.ResponseHeaderOverrides.ContentType = contentType;
            }
            
            var url = _s3Client.GetPreSignedURL(request);
            return url;
        }
        catch (Exception ex)
        {
            _logger.LogError($"生成预签名URL失败: {ex.Message}");
            return string.Empty;
        }
    }
    
    /// <summary>
    /// 获取存储桶中的文件总数
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <returns>文件总数</returns>
    public async Task<long> GetBucketFileCountAsync(string bucketName)
    {
        try
        {
            long totalCount = 0;
            string? continuationToken = null;

            do
            {
                var request = new ListObjectsV2Request
                {
                    BucketName = bucketName,
                    ContinuationToken = continuationToken
                };

                var response = await _s3Client.ListObjectsV2Async(request);
                
                // 只计算文件，不计算文件夹
                totalCount += response.S3Objects.Count(obj => !obj.Key.EndsWith("/"));
                
                continuationToken = response.NextContinuationToken;
            } while (continuationToken != null);

            _logger.LogInformation($"存储桶 '{bucketName}' 共有 {totalCount} 个文件");
            return totalCount;
        }
        catch (AmazonS3Exception ex)
        {
            _logger.LogError($"获取存储桶 '{bucketName}' 文件数失败：{ex.Message}");
            return 0;
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取文件数异常：{ex.Message}");
            return 0;
        }
    }
    
    /// <summary>
    /// 修复已上传文件的Content-Type元数据
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <param name="key">文件Key</param>
    /// <returns>是否成功</returns>
    public async Task<bool> FixFileContentTypeAsync(string bucketName, string key)
    {
        try
        {
            // 获取正确的 Content-Type
            var contentType = GetContentType(key);
            
            // 复制对象到自身，只更新元数据
            var copyRequest = new CopyObjectRequest
            {
                SourceBucket = bucketName,
                SourceKey = key,
                DestinationBucket = bucketName,
                DestinationKey = key,
                ContentType = contentType,
                MetadataDirective = S3MetadataDirective.REPLACE
            };
            
            await _s3Client.CopyObjectAsync(copyRequest);
            _logger.LogInformation($"修复文件Content-Type成功: {key}, ContentType: {contentType}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"修复文件Content-Type失败: {key}, 错误: {ex.Message}");
            return false;
        }
    }
    
    /// <summary>
    /// 根据文件扩展名获取Content-Type
    /// </summary>
    /// <param name="fileName">文件名或路径</param>
    /// <returns>MIME类型</returns>
    private string GetContentType(string fileName)
    {
        var extension = Path.GetExtension(fileName).ToLowerInvariant();
        return extension switch
        {
            // 图片
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".gif" => "image/gif",
            ".bmp" => "image/bmp",
            ".webp" => "image/webp",
            ".svg" => "image/svg+xml",
            ".ico" => "image/x-icon",
            ".tiff" or ".tif" => "image/tiff",
            ".heic" => "image/heic",
            ".heif" => "image/heif",
            ".avif" => "image/avif",
            
            // 视频
            ".mp4" => "video/mp4",
            ".webm" => "video/webm",
            ".ogv" => "video/ogg",
            ".avi" => "video/x-msvideo",
            ".mov" => "video/quicktime",
            ".wmv" => "video/x-ms-wmv",
            ".flv" => "video/x-flv",
            ".mkv" => "video/x-matroska",
            ".m4v" => "video/x-m4v",
            ".3gp" => "video/3gpp",
            ".ts" => "video/mp2t",
            
            // 音频
            ".mp3" => "audio/mpeg",
            ".wav" => "audio/wav",
            ".oga" => "audio/ogg",
            ".m4a" => "audio/mp4",
            ".flac" => "audio/flac",
            ".aac" => "audio/aac",
            ".wma" => "audio/x-ms-wma",
            ".opus" => "audio/opus",
            
            // 文档
            ".pdf" => "application/pdf",
            ".doc" => "application/msword",
            ".docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xls" => "application/vnd.ms-excel",
            ".xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".ppt" => "application/vnd.ms-powerpoint",
            ".pptx" => "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".odt" => "application/vnd.oasis.opendocument.text",
            ".ods" => "application/vnd.oasis.opendocument.spreadsheet",
            ".odp" => "application/vnd.oasis.opendocument.presentation",
            ".txt" => "text/plain; charset=utf-8",
            ".csv" => "text/csv; charset=utf-8",
            ".rtf" => "application/rtf",
            
            // 代码和标记语言
            ".html" or ".htm" => "text/html; charset=utf-8",
            ".css" => "text/css; charset=utf-8",
            ".js" or ".mjs" => "application/javascript; charset=utf-8",
            ".json" => "application/json; charset=utf-8",
            ".xml" => "application/xml; charset=utf-8",
            ".md" or ".markdown" => "text/markdown; charset=utf-8",
            ".yaml" or ".yml" => "text/yaml; charset=utf-8",
            ".toml" => "application/toml; charset=utf-8",
            ".sh" => "application/x-sh; charset=utf-8",
            ".py" => "text/x-python; charset=utf-8",
            ".java" => "text/x-java-source; charset=utf-8",
            ".c" => "text/x-c; charset=utf-8",
            ".cpp" or ".cc" => "text/x-c++; charset=utf-8",
            ".cs" => "text/x-csharp; charset=utf-8",
            ".go" => "text/x-go; charset=utf-8",
            ".rs" => "text/x-rustsrc; charset=utf-8",
            ".php" => "text/x-php; charset=utf-8",
            ".rb" => "text/x-ruby; charset=utf-8",
            
            // 字体
            ".woff" => "font/woff",
            ".woff2" => "font/woff2",
            ".ttf" => "font/ttf",
            ".otf" => "font/otf",
            ".eot" => "application/vnd.ms-fontobject",
            
            // 电子书
            ".epub" => "application/epub+zip",
            ".mobi" => "application/x-mobipocket-ebook",
            
            // 压缩文件
            ".zip" => "application/zip",
            ".rar" => "application/x-rar-compressed",
            ".7z" => "application/x-7z-compressed",
            ".tar" => "application/x-tar",
            ".gz" => "application/gzip",
            ".bz2" => "application/x-bzip2",
            ".xz" => "application/x-xz",
            
            // 其他
            ".apk" => "application/vnd.android.package-archive",
            ".exe" => "application/x-msdownload",
            ".dmg" => "application/x-apple-diskimage",
            ".iso" => "application/x-iso9660-image",
            
            // 默认
            _ => "application/octet-stream"
        };
    }
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

public class S3FilePageResult
{
    public List<S3ObjectInfo> Files { get; set; } = new();
    public bool IsTruncated { get; set; }
    public string? NextContinuationToken { get; set; }
    public int KeyCount { get; set; }
}

