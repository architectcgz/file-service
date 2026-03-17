using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using FileService.Config;
using System.Security.Cryptography;
using System.Text;

namespace FileService.Controllers;

/// <summary>
/// 管理员控制器
/// </summary>
[ApiController]
[Route("api/admin")]
public class AdminController : ControllerBase
{
    private readonly FileServiceSecurityConfig _securityConfig;
    private readonly ILogger<AdminController> _logger;

    public AdminController(
        IOptions<FileServiceSecurityConfig> securityConfig,
        ILogger<AdminController> logger)
    {
        _securityConfig = securityConfig.Value;
        _logger = logger;
    }

    /// <summary>
    /// 管理员登录
    /// </summary>
    [HttpPost("login")]
    public IActionResult Login([FromBody] AdminLoginDto request)
    {
        try
        {
            // 验证用户名和密码
            if (string.IsNullOrEmpty(request.Username) || string.IsNullOrEmpty(request.Password))
            {
                return BadRequest(new
                {
                    success = false,
                    message = "用户名和密码不能为空"
                });
            }

            // 从配置中获取管理员凭据
            var adminUsername = _securityConfig.AdminUsername ?? "admin";
            var adminPasswordHash = _securityConfig.AdminPasswordHash;

            if (string.IsNullOrEmpty(adminPasswordHash))
            {
                _logger.LogError("管理员密码哈希未配置");
                return StatusCode(500, new
                {
                    success = false,
                    message = "服务器配置错误"
                });
            }

            // 验证用户名
            if (request.Username != adminUsername)
            {
                _logger.LogWarning("管理员登录失败 - 无效的用户名: {Username}", request.Username);
                return Unauthorized(new
                {
                    success = false,
                    message = "用户名或密码错误"
                });
            }

            // 验证密码
            var passwordHash = HashPassword(request.Password);
            if (passwordHash != adminPasswordHash)
            {
                _logger.LogWarning("管理员登录失败 - 密码错误");
                return Unauthorized(new
                {
                    success = false,
                    message = "用户名或密码错误"
                });
            }

            // 登录成功，设置Session
            HttpContext.Session.SetString("IsAdminLoggedIn", "true");
            HttpContext.Session.SetString("AdminUsername", adminUsername);
            HttpContext.Session.SetString("LoginTime", DateTime.UtcNow.ToString("O"));

            _logger.LogInformation("管理员登录成功 - Username: {Username}", adminUsername);

            return Ok(new
            {
                success = true,
                message = "登录成功",
                username = adminUsername
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "管理员登录异常");
            return StatusCode(500, new
            {
                success = false,
                message = "登录失败"
            });
        }
    }

    /// <summary>
    /// 管理员登出
    /// </summary>
    [HttpPost("logout")]
    public IActionResult Logout()
    {
        HttpContext.Session.Clear();
        return Ok(new
        {
            success = true,
            message = "已登出"
        });
    }

    /// <summary>
    /// 获取当前登录状态
    /// </summary>
    [HttpGet("status")]
    public IActionResult GetStatus()
    {
        var isLoggedIn = HttpContext.Session.GetString("IsAdminLoggedIn") == "true";
        var username = HttpContext.Session.GetString("AdminUsername");

        return Ok(new
        {
            isLoggedIn,
            username = isLoggedIn ? username : null
        });
    }

    /// <summary>
    /// 生成密码哈希（用于配置文件）
    /// </summary>
    [HttpPost("generate-password-hash")]
    public IActionResult GeneratePasswordHash([FromBody] GenerateHashDto request)
    {
        if (string.IsNullOrEmpty(request.Password))
        {
            return BadRequest(new
            {
                success = false,
                message = "密码不能为空"
            });
        }

        var hash = HashPassword(request.Password);
        return Ok(new
        {
            success = true,
            passwordHash = hash,
            message = "将此哈希值配置到 FileServiceSecurity.AdminPasswordHash"
        });
    }

    /// <summary>
    /// 计算密码SHA256哈希
    /// </summary>
    private static string HashPassword(string password)
    {
        using var sha256 = SHA256.Create();
        var bytes = Encoding.UTF8.GetBytes(password);
        var hash = sha256.ComputeHash(bytes);
        return Convert.ToHexString(hash).ToLower();
    }
}

/// <summary>
/// 管理员登录DTO
/// </summary>
public class AdminLoginDto
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

/// <summary>
/// 生成密码哈希DTO
/// </summary>
public class GenerateHashDto
{
    public string Password { get; set; } = string.Empty;
}
