using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace FileService.Attributes;

/// <summary>
/// 管理员认证特性
/// 支持两种认证方式：
/// 1. Session认证（用户登录后）
/// 2. Token认证（API调用）
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Method)]
public class AdminAuthAttribute : Attribute, IAuthorizationFilter
{
    public void OnAuthorization(AuthorizationFilterContext context)
    {
        // 方式1: 检查Session认证
        var isAdminLoggedIn = context.HttpContext.Session.GetString("IsAdminLoggedIn");
        if (isAdminLoggedIn == "true")
        {
            return; // Session认证通过
        }

        // 方式2: 检查Token认证（用于API调用）
        if (context.HttpContext.Request.Headers.TryGetValue("X-Admin-Token", out var tokenHeader))
        {
            var token = tokenHeader.ToString();
            var configuration = context.HttpContext.RequestServices
                .GetRequiredService<IConfiguration>();
            
            var validToken = configuration["FileServiceSecurity:AdminToken"];
            
            if (!string.IsNullOrEmpty(validToken) && token == validToken)
            {
                return; // Token认证通过
            }
        }

        // 认证失败
        context.Result = new ObjectResult(new
        {
            success = false,
            message = "需要管理员权限"
        })
        {
            StatusCode = StatusCodes.Status401Unauthorized
        };
    }
}
