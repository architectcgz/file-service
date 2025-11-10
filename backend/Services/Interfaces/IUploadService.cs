using Microsoft.AspNetCore.Http;
using FileService.Models.Dto;

namespace FileService.Services.Interfaces;

public interface IUploadService
{
    Task<string> UploadFileAsync(IFormFile file, string? uploaderId = null);
    Task<string> GetUploadToken();
    
    /// <summary>
    /// 生成文件直传签名
    /// </summary>
    /// <param name="request">直传签名请求</param>
    /// <param name="fileCategory">文件分类（可选）</param>
    /// <param name="maxSizeBytes">最大文件大小限制</param>
    /// <returns>直传签名响应</returns>
    Task<DirectUploadSignatureResponseDto> GetDirectUploadSignatureAsync(DirectUploadSignatureRequestDto request, string? fileCategory = null, long maxSizeBytes = 10485760);
    
    /// <summary>
    /// 生成预签名URL
    /// </summary>
    /// <param name="request">预签名URL请求</param>
    /// <returns>预签名URL响应</returns>
    Task<PresignedUrlResponseDto> GetPresignedUrlAsync(PresignedUrlRequestDto request);
    
    /// <summary>
    /// 记录直传完成的文件信息
    /// </summary>
    /// <param name="request">文件记录请求</param>
    /// <param name="uploaderId">上传者用户ID（可选）</param>
    /// <returns>记录结果</returns>
    Task<RecordDirectUploadResponseDto> RecordDirectUploadAsync(RecordDirectUploadRequestDto request, string? uploaderId = null);
}

