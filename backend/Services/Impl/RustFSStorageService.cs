using Amazon.S3;
using Amazon.S3.Model;
using FileService.Config;
using FileService.Constants;
using FileService.Exceptions;
using FileService.Services.Interfaces;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Options;
using System.Net;

namespace FileService.Services.Impl;

/// <summary>
/// RustFS 存储服务实现
/// </summary>
public class RustFSStorageService : IStorageService
{
    private readonly IAmazonS3 _s3Client;
    private readonly RustFSConfig _config;
    private readonly ILogger<RustFSStorageService> _logger;
    private readonly IMemoryCache _memoryCache;

    public RustFSStorageService(
        IAmazonS3 s3Client,
        IOptions<RustFSConfig> config,
        ILogger<RustFSStorageService> logger,
        IMemoryCache memoryCache)
    {
        _s3Client = s3Client;
        _config = config.Value;
        _logger = logger;
        _memoryCache = memoryCache;
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

    // ==================== 从 RustFSUtil 迁移的方法 ====================

    /// <summary>
    /// 上传流数据到存储服务
    /// </summary>
    public async Task<bool> UploadStreamAsync(Stream stream, string fileName, string bucket)
    {
        try
        {
            var contentType = GetContentType(fileName);
            
            var putRequest = new PutObjectRequest
            {
                BucketName = bucket,
                Key = fileName,
                InputStream = stream,
                ContentType = contentType
            };

            await _s3Client.PutObjectAsync(putRequest);

            _logger.LogInformation($"上传成功，文件Key: {fileName}, 存储桶: {bucket}, ContentType: {contentType}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"上传异常：{ex.Message}");
            return false;
        }
    }

    /// <summary>
    /// 生成预签名URL（指定bucket，支持下载模式）
    /// </summary>
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
    /// 清除预签名URL缓存
    /// </summary>
    public void ClearPresignedUrlCache(string key, int? expiresInMinutes = null)
    {
        try
        {
            if (expiresInMinutes.HasValue)
            {
                var cacheKey = $"presigned_url:{key}:{expiresInMinutes.Value}";
                _memoryCache.Remove(cacheKey);
                _logger.LogInformation($"已清除预签名URL缓存: {cacheKey}");
            }
            else
            {
                _logger.LogInformation($"请求清除文件的所有预签名URL缓存: {key}");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"清除预签名URL缓存异常: {ex.Message}");
        }
    }

    /// <summary>
    /// 列出所有bucket
    /// </summary>
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
    /// 列出bucket中的文件夹和根目录文件
    /// </summary>
    public async Task<BucketContentsResult> ListFoldersAndFilesAsync(string bucketName)
    {
        try
        {
            var request = new ListObjectsV2Request
            {
                BucketName = bucketName,
                Delimiter = "/"
            };
            
            var response = await _s3Client.ListObjectsV2Async(request);
            
            var folders = response.CommonPrefixes
                .Select(prefix => prefix.TrimEnd('/'))
                .Where(folder => !string.IsNullOrEmpty(folder))
                .ToList();
            
            var files = response.S3Objects
                .Where(obj => !obj.Key.EndsWith("/"))
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
            
            _logger.LogInformation($"从存储桶 '{bucketName}' 列出 {folders.Count} 个文件夹和 {files.Count} 个根目录文件");
            return new BucketContentsResult
            {
                Folders = folders,
                Files = files
            };
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出存储桶内容异常：{ex.Message}");
            return new BucketContentsResult
            {
                Folders = new List<string>(),
                Files = new List<S3ObjectInfo>()
            };
        }
    }

    /// <summary>
    /// 列出指定文件夹下的文件（分页）
    /// </summary>
    public async Task<S3FilePageResult> ListFilesInFolderAsync(string bucketName, string folder, int maxKeys = 20, string? continuationToken = null)
    {
        try
        {
            var prefix = string.IsNullOrEmpty(folder) ? "" : folder.TrimEnd('/') + "/";
            var request = new ListObjectsV2Request
            {
                BucketName = bucketName,
                Prefix = prefix,
                Delimiter = "/",
                MaxKeys = maxKeys,
                ContinuationToken = continuationToken
            };
            
            var response = await _s3Client.ListObjectsV2Async(request);
            
            var files = response.S3Objects
                .Where(obj => !obj.Key.EndsWith("/"))
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
        catch (Exception ex)
        {
            _logger.LogError($"列出文件夹文件异常：{ex.Message}");
            return new S3FilePageResult { Files = new List<S3ObjectInfo>() };
        }
    }

    /// <summary>
    /// 删除bucket
    /// </summary>
    public async Task<bool> DeleteBucketAsync(string bucketName)
    {
        try
        {
            var bucketExists = await Amazon.S3.Util.AmazonS3Util.DoesS3BucketExistV2Async(_s3Client, bucketName);
            if (!bucketExists)
            {
                _logger.LogInformation($"存储桶不存在: {bucketName}");
                return false;
            }

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
    /// 检查bucket是否存在
    /// </summary>
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
    /// 创建目录
    /// </summary>
    public async Task<bool> CreateDirectoryAsync(string bucketName, string directoryPath)
    {
        try
        {
            var normalizedPath = directoryPath.TrimEnd('/') + "/";
            
            if (await DirectoryExistsAsync(bucketName, normalizedPath))
            {
                _logger.LogInformation($"目录已存在: {bucketName}/{normalizedPath}");
                return true;
            }

            var putRequest = new PutObjectRequest
            {
                BucketName = bucketName,
                Key = normalizedPath,
                ContentBody = string.Empty,
                ContentType = "application/x-directory"
            };

            await _s3Client.PutObjectAsync(putRequest);
            _logger.LogInformation($"目录创建成功: {bucketName}/{normalizedPath}");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError($"创建目录异常：{ex.Message}, 存储桶: {bucketName}, 目录: {directoryPath}");
            return false;
        }
    }

    /// <summary>
    /// 检查目录是否存在
    /// </summary>
    public async Task<bool> DirectoryExistsAsync(string bucketName, string directoryPath)
    {
        try
        {
            var normalizedPath = directoryPath.TrimEnd('/') + "/";
            
            try
            {
                var request = new GetObjectMetadataRequest
                {
                    BucketName = bucketName,
                    Key = normalizedPath
                };
                await _s3Client.GetObjectMetadataAsync(request);
                return true;
            }
            catch (AmazonS3Exception ex) when (ex.StatusCode == HttpStatusCode.NotFound)
            {
                var listRequest = new ListObjectsV2Request
                {
                    BucketName = bucketName,
                    Prefix = normalizedPath,
                    MaxKeys = 1
                };
                
                var response = await _s3Client.ListObjectsV2Async(listRequest);
                return response.S3Objects.Count > 0;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"检查目录存在性异常：{ex.Message}, 存储桶: {bucketName}, 目录: {directoryPath}");
            return false;
        }
    }

    /// <summary>
    /// 获取bucket中的文件总数
    /// </summary>
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
                totalCount += response.S3Objects.Count(obj => !obj.Key.EndsWith("/"));
                continuationToken = response.NextContinuationToken;
            } while (continuationToken != null);

            _logger.LogInformation($"存储桶 '{bucketName}' 共有 {totalCount} 个文件");
            return totalCount;
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取文件数异常：{ex.Message}");
            return 0;
        }
    }

    /// <summary>
    /// 修复文件的ContentType元数据
    /// </summary>
    public async Task<bool> FixFileContentTypeAsync(string bucketName, string key)
    {
        try
        {
            var contentType = GetContentType(key);
            
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
