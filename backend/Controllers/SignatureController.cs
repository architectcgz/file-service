using Microsoft.AspNetCore.Mvc;
using FileService.Models.Dto;
using FileService.Services.Interfaces;
using FileService.Attributes;

namespace FileService.Controllers;

/// <summary>
/// 签名管理控制器
/// </summary>
[ApiController]
[Route("api/admin/signatures")]
[AdminAuth] // 需要管理员认证
public class SignatureController : ControllerBase
{
    private readonly ISignatureService _signatureService;
    private readonly ILogger<SignatureController> _logger;

    public SignatureController(
        ISignatureService signatureService,
        ILogger<SignatureController> logger)
    {
        _signatureService = signatureService;
        _logger = logger;
    }

    /// <summary>
    /// 颁发新签名
    /// </summary>
    [HttpPost("issue")]
    public async Task<IActionResult> IssueSignature([FromBody] SignatureIssueRequestDto request)
    {
        var clientIp = HttpContext.Connection.RemoteIpAddress?.ToString();
        var result = await _signatureService.IssueSignatureAsync(request, clientIp);

        if (!result.Success)
        {
            return BadRequest(result);
        }

        return Ok(result);
    }

    /// <summary>
    /// 获取签名列表
    /// </summary>
    [HttpGet]
    public async Task<IActionResult> GetSignatures([FromQuery] SignatureQueryDto query)
    {
        var result = await _signatureService.GetSignaturesAsync(query);
        return Ok(result);
    }

    /// <summary>
    /// 获取签名详情
    /// </summary>
    [HttpGet("{signatureToken}")]
    public async Task<IActionResult> GetSignature(string signatureToken)
    {
        var signature = await _signatureService.GetSignatureAsync(signatureToken);
        if (signature == null)
        {
            return NotFound(new { success = false, message = "签名不存在" });
        }

        return Ok(signature);
    }

    /// <summary>
    /// 撤销签名
    /// </summary>
    [HttpPost("{signatureToken}/revoke")]
    public async Task<IActionResult> RevokeSignature(
        string signatureToken,
        [FromBody] RevokeRequestDto request)
    {
        var success = await _signatureService.RevokeSignatureAsync(signatureToken, request.Reason);
        if (!success)
        {
            return NotFound(new { success = false, message = "签名不存在或撤销失败" });
        }

        return Ok(new { success = true, message = "签名已撤销" });
    }

    /// <summary>
    /// 批量撤销签名
    /// </summary>
    [HttpPost("batch-revoke")]
    public async Task<IActionResult> BatchRevoke([FromBody] BatchRevokeRequestDto request)
    {
        var count = await _signatureService.RevokeBatchSignaturesAsync(
            request.SignatureTokens,
            request.Reason);

        return Ok(new
        {
            success = true,
            message = $"已撤销 {count} 个签名",
            revokedCount = count
        });
    }

    /// <summary>
    /// 更新签名过期时间
    /// </summary>
    [HttpPut("{signatureToken}/expiry")]
    public async Task<IActionResult> UpdateExpiry(
        string signatureToken,
        [FromBody] UpdateExpiryRequestDto request)
    {
        var success = await _signatureService.UpdateExpiryTimeAsync(
            signatureToken,
            request.NewExpiryTime);

        if (!success)
        {
            return NotFound(new { success = false, message = "签名不存在或更新失败" });
        }

        return Ok(new { success = true, message = "过期时间已更新" });
    }

    /// <summary>
    /// 清理过期签名
    /// </summary>
    [HttpPost("clean-expired")]
    public async Task<IActionResult> CleanExpired()
    {
        var count = await _signatureService.CleanExpiredSignaturesAsync();
        return Ok(new
        {
            success = true,
            message = $"已清理 {count} 个过期签名",
            cleanedCount = count
        });
    }

    /// <summary>
    /// 获取统计信息
    /// </summary>
    [HttpGet("statistics")]
    public async Task<IActionResult> GetStatistics()
    {
        var statistics = await _signatureService.GetStatisticsAsync();
        return Ok(statistics);
    }
}

/// <summary>
/// 撤销请求DTO
/// </summary>
public class RevokeRequestDto
{
    public string Reason { get; set; } = string.Empty;
}
