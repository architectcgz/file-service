using FileService.Models.Dto;
using FileService.Repositories.Entities;

namespace FileService.Services.Interfaces;

/// <summary>
/// 签名管理服务接口
/// </summary>
public interface ISignatureService
{
    /// <summary>
    /// 颁发新签名
    /// </summary>
    Task<SignatureIssueResponseDto> IssueSignatureAsync(SignatureIssueRequestDto request, string? creatorIp);

    /// <summary>
    /// 验证签名
    /// </summary>
    Task<SignatureValidationResultDto> ValidateSignatureAsync(string signatureToken, string operation, string? fileType = null);

    /// <summary>
    /// 记录签名使用
    /// </summary>
    Task RecordSignatureUsageAsync(string signatureToken);

    /// <summary>
    /// 手动撤销签名
    /// </summary>
    Task<bool> RevokeSignatureAsync(string signatureToken, string reason);

    /// <summary>
    /// 批量撤销签名
    /// </summary>
    Task<int> RevokeBatchSignaturesAsync(List<string> signatureTokens, string reason);

    /// <summary>
    /// 清理过期签名
    /// </summary>
    Task<int> CleanExpiredSignaturesAsync();

    /// <summary>
    /// 获取签名列表
    /// </summary>
    Task<PagedResultDto<ApiSignature>> GetSignaturesAsync(SignatureQueryDto query);

    /// <summary>
    /// 获取签名详情
    /// </summary>
    Task<ApiSignature?> GetSignatureAsync(string signatureToken);

    /// <summary>
    /// 获取签名统计信息
    /// </summary>
    Task<SignatureStatisticsDto> GetStatisticsAsync();

    /// <summary>
    /// 更新签名过期时间
    /// </summary>
    Task<bool> UpdateExpiryTimeAsync(string signatureToken, DateTime newExpiryTime);
}
