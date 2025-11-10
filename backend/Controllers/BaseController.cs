using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using FileService.Config;

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
    /// 验证请求来源（通过共享密钥）
    /// </summary>
    /// <returns>如果验证通过返回true，否则返回false（已设置403响应）</returns>
    protected bool ValidateRequestSource()
    {
        var securityConfig = HttpContext.RequestServices.GetRequiredService<IOptions<FileServiceSecurityConfig>>().Value;
        
        // 如果未启用共享密钥验证，直接通过
        if (!securityConfig.EnableSharedSecretValidation)
        {
            return true;
        }

        // 检查共享密钥
        if (string.IsNullOrWhiteSpace(securityConfig.SharedSecret))
        {
            Response.StatusCode = StatusCodes.Status500InternalServerError;
            return false;
        }

        // 从请求头获取共享密钥
        if (!Request.Headers.TryGetValue("X-Service-Secret", out var secretHeader))
        {
            Response.StatusCode = StatusCodes.Status403Forbidden;
            return false;
        }

        var providedSecret = secretHeader.ToString();
        if (providedSecret != securityConfig.SharedSecret)
        {
            Response.StatusCode = StatusCodes.Status403Forbidden;
            return false;
        }

        return true;
    }
}

