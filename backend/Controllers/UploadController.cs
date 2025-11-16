using Microsoft.AspNetCore.Mvc;
using FileService.Models.Dto;
using FileService.Services.Interfaces;
using FileService.Utils;

namespace FileService.Controllers;

[ApiController]
[Route("api/upload")]
public class UploadController(IUploadService uploadService) : BaseController
{
    [HttpPost("rustfs")]
    public async Task<IActionResult> Upload(IFormFile file)
    {
        try
        {
            // 验证请求来源（必须来自 blog-api）
            if (!ValidateRequestSource())
            {
                return StatusCode(StatusCodes.Status403Forbidden, new UploadResponseDto
                {
                    Success = false,
                    Message = "请求来源验证失败"
                });
            }

            // 从请求头获取用户ID（由 blog-api 传递）
            var userId = GetCurrentUserId();
            
            var result = await uploadService.UploadFileAsync(file, userId);
            if (string.IsNullOrEmpty(result))
            {
                return BadRequest(new UploadResponseDto
                {
                    Success = false,
                    Message = "上传失败"
                });
            }

            // result 已经是完整的 URL，直接返回
            return Ok(new UploadResponseDto
            {
                Url = result,  // UploadFileAsync 返回的已经是完整的代理 URL
                Key = Path.GetFileName(result),
                Success = true,
                Message = "上传成功"
            });
        }
        catch (Exception ex)
        {
            // 记录详细错误信息到日志
            var logger = HttpContext.RequestServices.GetRequiredService<ILogger<UploadController>>();
            logger.LogError($"文件上传失败: {ex.Message}");
            logger.LogError($"异常详情: {ex}");

            return BadRequest(new UploadResponseDto
            {
                Success = false,
                Message = $"上传失败: {ex.Message}"
            });
        }
    }
    
    
    /// <summary>
    /// 获取文件直传签名（通用接口，根据 FileType 自动判断类别）
    /// </summary>
    [HttpPost("direct-signature")]
    public async Task<IActionResult> GetDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        // 根据 MIME 类型自动判断文件类别和大小限制
        var (fileCategory, maxSizeBytes) = DetermineFileCategoryAndSize(request.FileType);
        
        return await GetFileDirectUploadSignature(request, fileCategory, maxSizeBytes);
    }

    /// <summary>
    /// 获取文档直传签名（S3 POST策略签名）
    /// </summary>
    [HttpPost("direct-signature/document")]
    public async Task<IActionResult> GetDocumentDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        return await GetFileDirectUploadSignature(request, "document");
    }

    /// <summary>
    /// 获取视频直传签名（S3 POST策略签名）
    /// </summary>
    [HttpPost("direct-signature/video")]
    public async Task<IActionResult> GetVideoDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        return await GetFileDirectUploadSignature(request, "video");
    }

    /// <summary>
    /// 获取音频直传签名（S3 POST策略签名）
    /// </summary>
    [HttpPost("direct-signature/audio")]
    public async Task<IActionResult> GetAudioDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        return await GetFileDirectUploadSignature(request, "audio");
    }

    /// <summary>
    /// 获取压缩包直传签名（S3 POST策略签名）
    /// </summary>
    [HttpPost("direct-signature/archive")]
    public async Task<IActionResult> GetArchiveDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        return await GetFileDirectUploadSignature(request, "archive");
    }

    /// <summary>
    /// 获取大文件直传签名（支持分片上传）
    /// </summary>
    [HttpPost("direct-signature/large-file")]
    public async Task<IActionResult> GetLargeFileDirectUploadSignature([FromBody] DirectUploadSignatureRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        return await GetFileDirectUploadSignature(request, "large", maxSizeBytes: 1073741824); // 1GB
    }

    /// <summary>
    /// 根据 MIME 类型判断文件类别和大小限制
    /// </summary>
    /// <param name="mimeType">MIME 类型</param>
    /// <returns>文件类别和最大大小限制（字节）</returns>
    private (string fileCategory, long maxSizeBytes) DetermineFileCategoryAndSize(string mimeType)
    {
        if (string.IsNullOrEmpty(mimeType))
        {
            return ("image", 10485760); // 默认图片，10MB
        }

        // 图片类型
        if (mimeType.StartsWith("image/"))
        {
            return ("image", 10485760); // 10MB
        }

        // 视频类型
        if (mimeType.StartsWith("video/"))
        {
            return ("video", 524288000); // 500MB
        }

        // 音频类型
        if (mimeType.StartsWith("audio/"))
        {
            return ("audio", 52428800); // 50MB
        }

        // 文档类型
        if (mimeType.Contains("pdf") || 
            mimeType.Contains("document") || 
            mimeType.Contains("text") ||
            mimeType.Contains("msword") ||
            mimeType.Contains("officedocument") ||
            mimeType.Contains("excel") ||
            mimeType.Contains("powerpoint") ||
            mimeType.Contains("spreadsheet") ||
            mimeType.Contains("presentation"))
        {
            return ("document", 52428800); // 50MB
        }

        // 压缩包类型
        if (mimeType.Contains("zip") || 
            mimeType.Contains("rar") || 
            mimeType.Contains("7z") ||
            mimeType.Contains("tar") ||
            mimeType.Contains("gzip") ||
            mimeType.Contains("compressed"))
        {
            return ("archive", 104857600); // 100MB
        }

        // 默认类型
        return ("image", 10485760); // 默认图片，10MB
    }

    /// <summary>
    /// 通用文件直传签名生成方法（支持去重检查）
    /// </summary>
    private async Task<IActionResult> GetFileDirectUploadSignature(DirectUploadSignatureRequestDto request, string fileCategory, long maxSizeBytes = 10485760)
    {
        // 验证服务名，如果未提供则使用空字符串
        if (request.Service == null)
        {
            request.Service = string.Empty;
        }
        
        var result = await uploadService.GetDirectUploadSignatureAsync(request, fileCategory, maxSizeBytes);
        
        if (!result.Success)
        {
            return BadRequest(new { success = false, message = result.Message });
        }

        // 如果文件已存在（去重命中），直接返回已有URL
        if (!result.NeedUpload && !string.IsNullOrEmpty(result.ExistingFileUrl))
        {
            return Ok(new
            {
                success = true,
                needUpload = false,  // 告诉前端无需上传
                existingFileUrl = result.ExistingFileUrl,
                fileKey = result.FileKey,
                fileHash = result.FileHash,
                message = result.Message
            });
        }

        // 文件不存在，需要上传，返回签名（FileUrl 已在 UploadService 中生成）
        return Ok(new
        {
            success = true,
            needUpload = true,  // 需要上传
            signature = result.Signature,
            fileUrl = result.FileUrl,  // 使用 UploadService 返回的完整 URL（包含 bucket）
            fileKey = result.FileKey,
            fileHash = result.FileHash,
            bucketName = result.BucketName,  // 返回 bucket 名称
            fileCategory = fileCategory,  // 返回文件分类
            maxSizeBytes = maxSizeBytes  // 返回最大文件大小限制
        });
    }

    /// <summary>
    /// 记录直传完成的文件信息
    /// </summary>
    [HttpPost("record-direct-upload")]
    public async Task<IActionResult> RecordDirectUpload([FromBody] RecordDirectUploadRequestDto request)
    {
        // 前端直传不需要验证SharedSecret，由nginx控制访问源
        
        // 从请求头获取用户ID（由 blog-api 传递）
        var userId = GetCurrentUserId();
        
        // 验证服务名，如果未提供则使用空字符串
        if (request.Service == null)
        {
            request.Service = string.Empty;
        }
        
        var result = await uploadService.RecordDirectUploadAsync(request, userId);
        
        if (!result.Success)
        {
            return BadRequest(new { success = false, message = result.Message });
        }

        return Ok(new
        {
            success = true,
            fileId = result.FileId,
            message = result.Message
        });
    }
    
    /// <summary>
    /// 获取预签名URL（用于访问私有桶中的文件）
    /// </summary>
    [HttpPost("presigned-url")]
    public async Task<IActionResult> GetPresignedUrl([FromBody] PresignedUrlRequestDto request)
    {
        // 验证用户必须已登录
        if (!ValidateRequestSource())
        {
            return Unauthorized(new { success = false, message = "未登录，请先登录后再访问文件" });
        }

        var result = await uploadService.GetPresignedUrlAsync(request);
        
        if (!result.Success)
        {
            return BadRequest(new { success = false, message = result.Message });
        }

        return Ok(new
        {
            success = true,
            presignedUrl = result.PresignedUrl,
            expiresInMinutes = result.ExpiresInMinutes
        });
    }
}

