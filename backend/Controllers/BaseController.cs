using Microsoft.AspNetCore.Mvc;
using FileService.Services.Interfaces;
using FileService.Models.Dto;

namespace FileService.Controllers;

/// <summary>
/// 控制器基类，提供通用功能
/// </summary>
public abstract class BaseController : ControllerBase
{
    /// <summary>
    /// 从请求头获取用户ID（由业务服务传递）
    /// </summary>
    /// <returns>用户ID，如果未提供则返回null</returns>
    protected string? GetCurrentUserId()
    {
        // 从请求头获取用户ID，由调用方（如 blog-api）传递
        if (Request.Headers.TryGetValue("X-User-Id", out var userIdHeader))
        {
            return userIdHeader.ToString();
        }
        return null;
    }

    /// <summary>
    /// 验证API签名（用于签名管理系统）
    /// </summary>
    /// <param name="requiredOperation">需要的操作类型（如 upload, download）</param>
    /// <param name="fileType">文件类型（可选）</param>
    /// <returns>验证结果，如果失败已设置响应</returns>
    protected async Task<SignatureValidationResultDto?> ValidateSignatureAsync(string requiredOperation, string? fileType = null)
    {
        var signatureService = HttpContext.RequestServices.GetService<ISignatureService>();
        if (signatureService == null)
        {
            Response.StatusCode = StatusCodes.Status500InternalServerError;
            return null;
        }

        // 从请求头获取签名Token
        if (!Request.Headers.TryGetValue("X-Signature-Token", out var tokenHeader))
        {
            Response.StatusCode = StatusCodes.Status401Unauthorized;
            return null;
        }

        var signatureToken = tokenHeader.ToString();
        if (string.IsNullOrWhiteSpace(signatureToken))
        {
            Response.StatusCode = StatusCodes.Status401Unauthorized;
            return null;
        }

        // 验证签名
        var validationResult = await signatureService.ValidateSignatureAsync(signatureToken, requiredOperation, fileType);
        
        if (!validationResult.IsValid)
        {
            Response.StatusCode = StatusCodes.Status403Forbidden;
            return null;
        }

        // 记录签名使用（直接await，不使用Task.Run避免DbContext并发问题）
        try
        {
            await signatureService.RecordSignatureUsageAsync(signatureToken);
        }
        catch (Exception ex)
        {
            // 记录失败不影响主流程，只记录日志
            var logger = HttpContext.RequestServices.GetService<ILogger<BaseController>>();
            logger?.LogWarning(ex, "记录签名使用失败 - Token: {Token}", signatureToken);
        }

        return validationResult;
    }
}

