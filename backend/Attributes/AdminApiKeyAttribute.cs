using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.Extensions.Options;
using FileService.Config;

namespace FileService.Attributes;

/// <summary>
/// 管理员认证特性
/// 支持两种认证方式：
/// 1. Session认证（通过登录接口登录后使用）
/// 2. API Key认证（在请求头中提供 X-Admin-Api-Key）
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Method)]
public class AdminApiKeyAttribute : Attribute, IAuthorizationFilter
{
    public void OnAuthorization(AuthorizationFilterContext context)
    {
        var securityConfig = context.HttpContext.RequestServices
            .GetRequiredService<IOptions<FileServiceSecurityConfig>>().Value;

        // 如果未启用管理员验证，直接通过
        if (!securityConfig.EnableAdminApiKeyValidation)
        {
            return;
        }

        // 方式1：检查Session（已登录）
        var session = context.HttpContext.Session;
        var isLoggedIn = session.GetString("AdminLoggedIn") == "true";
        
        if (isLoggedIn)
        {
            // Session有效，验证通过
            return;
        }

        // 方式2：检查API Key（请求头）
        if (context.HttpContext.Request.Headers.TryGetValue("X-Admin-Api-Key", out var apiKeyHeader))
        {
            var providedApiKey = apiKeyHeader.ToString();
            
            // 检查管理员API Key是否配置
            if (string.IsNullOrWhiteSpace(securityConfig.AdminApiKey))
            {
                context.Result = new ObjectResult(new { success = false, message = "管理员API Key未配置" })
                {
                    StatusCode = StatusCodes.Status500InternalServerError
                };
                return;
            }

            if (providedApiKey == securityConfig.AdminApiKey)
            {
                // API Key有效，验证通过
                return;
            }
        }

        // 两种方式都未通过，返回未授权
        context.Result = new ObjectResult(new { success = false, message = "未授权，请先登录或提供有效的管理员API Key" })
        {
            StatusCode = StatusCodes.Status401Unauthorized
        };
    }
}

