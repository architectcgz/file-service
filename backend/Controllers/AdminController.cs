using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using FileService.Attributes;
using FileService.Config;
using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Utils;

namespace FileService.Controllers;

/// <summary>
/// 管理员接口控制器
/// 支持两种认证方式：
/// 1. Session认证（通过登录接口登录）
/// 2. API Key认证（在请求头中提供 X-Admin-Api-Key）
/// </summary>
[ApiController]
[Route("api/admin")]
public class AdminController(
    FileServiceDbContext dbContext,
    RustFSUtil rustFSUtil,
    IOptions<FileServiceSecurityConfig> securityConfig,
    ILogger<AdminController> logger) : ControllerBase
{
    private readonly FileServiceDbContext _dbContext = dbContext;
    private readonly RustFSUtil _rustFSUtil = rustFSUtil;
    private readonly FileServiceSecurityConfig _securityConfig = securityConfig.Value;
    private readonly ILogger<AdminController> _logger = logger;

    /// <summary>
    /// 管理员登录
    /// </summary>
    [HttpPost("login")]
    public IActionResult Login([FromBody] AdminLoginRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.Username) || string.IsNullOrWhiteSpace(request.Password))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "用户名和密码不能为空"
                });
            }

            // 验证用户名和密码
            if (string.IsNullOrWhiteSpace(_securityConfig.AdminUsername) || 
                string.IsNullOrWhiteSpace(_securityConfig.AdminPassword))
            {
                return StatusCode(500, new AdminResponseDto
                {
                    Success = false,
                    Message = "管理员账号未配置"
                });
            }

            if (request.Username != _securityConfig.AdminUsername || 
                request.Password != _securityConfig.AdminPassword)
            {
                return Unauthorized(new AdminResponseDto
                {
                    Success = false,
                    Message = "用户名或密码错误"
                });
            }

            // 设置Session
            HttpContext.Session.SetString("AdminLoggedIn", "true");
            HttpContext.Session.SetString("AdminUsername", request.Username);
            HttpContext.Session.SetString("LoginTime", DateTime.UtcNow.ToString("O"));

            _logger.LogInformation($"管理员登录成功: {request.Username}");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "登录成功",
                Data = new
                {
                    Username = request.Username,
                    SessionTimeoutMinutes = _securityConfig.AdminSessionTimeoutMinutes
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"登录异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"登录失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 管理员登出
    /// </summary>
    [HttpPost("logout")]
    [AdminApiKey]
    public IActionResult Logout()
    {
        try
        {
            HttpContext.Session.Clear();
            _logger.LogInformation("管理员登出成功");
            
            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "登出成功"
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"登出异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"登出失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 检查登录状态
    /// </summary>
    [HttpGet("status")]
    [AdminApiKey]
    public IActionResult GetStatus()
    {
        var isLoggedIn = HttpContext.Session.GetString("AdminLoggedIn") == "true";
        var username = HttpContext.Session.GetString("AdminUsername");
        var loginTime = HttpContext.Session.GetString("LoginTime");

        return Ok(new AdminResponseDto
        {
            Success = true,
            Message = "获取状态成功",
            Data = new
            {
                IsLoggedIn = isLoggedIn,
                Username = username,
                LoginTime = loginTime,
                SessionTimeoutMinutes = _securityConfig.AdminSessionTimeoutMinutes
            }
        });
    }

    /// <summary>
    /// 为指定服务创建存储表
    /// </summary>
    [HttpPost("tables/create")]
    [AdminApiKey]
    public async Task<IActionResult> CreateTable([FromBody] CreateTableRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.Service))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "服务名称不能为空"
                });
            }

            // 使用TableNameHelper生成表名
            var tableName = TableNameHelper.GetTableName(request.Service);

            // 检查表是否已存在
            var connection = _dbContext.Database.GetDbConnection();
            await connection.OpenAsync();
            try
            {
                var command = connection.CreateCommand();
                command.CommandText = @"
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_schema = 'public' 
                        AND table_name = @tableName
                    )";
                var param = command.CreateParameter();
                param.ParameterName = "@tableName";
                param.Value = tableName;
                command.Parameters.Add(param);

                var result = await command.ExecuteScalarAsync();
                var tableExists = result != null && (bool)result;

                if (tableExists)
                {
                    return Ok(new AdminResponseDto
                    {
                        Success = true,
                        Message = $"表 {tableName} 已存在",
                        Data = new { TableName = tableName, Service = request.Service }
                    });
                }

                // 创建表（使用FileServiceDbContextExtensions中的逻辑）
                var baseTableName = TableNameHelper.GetTableName(null);
                var createTableSql = $@"
                    CREATE TABLE {tableName} (LIKE {baseTableName} INCLUDING ALL);
                    
                    -- 复制索引
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_content_type ON {tableName} (content_type);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_create_time ON {tableName} (create_time);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_deleted ON {tableName} (deleted);
                    CREATE UNIQUE INDEX IF NOT EXISTS ix_{tableName}_file_hash ON {tableName} (file_hash) WHERE deleted = false;
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_reference_count ON {tableName} (reference_count);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_uploader_id ON {tableName} (uploader_id);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_service ON {tableName} (service);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_deleted_uploader_id ON {tableName} (deleted, uploader_id);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_content_type_create_time_id ON {tableName} (content_type, create_time DESC, id DESC);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_uploader_id_create_time_id ON {tableName} (uploader_id, create_time DESC, id DESC);
                ";

                await _dbContext.Database.ExecuteSqlRawAsync(createTableSql);

                _logger.LogInformation($"管理员创建存储表成功: {tableName}, 服务: {request.Service}");

                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"表 {tableName} 创建成功",
                    Data = new { TableName = tableName, Service = request.Service }
                });
            }
            finally
            {
                await connection.CloseAsync();
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"创建存储表异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"创建表失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 在RustFS创建存储桶
    /// </summary>
    [HttpPost("buckets/create")]
    [AdminApiKey]
    public async Task<IActionResult> CreateBucket([FromBody] CreateBucketRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.BucketName))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称不能为空"
                });
            }

            // 验证存储桶名称格式（S3存储桶命名规则）
            if (!IsValidBucketName(request.BucketName))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称格式无效。存储桶名称必须符合S3命名规则：3-63个字符，只能包含小写字母、数字、点和连字符"
                });
            }

            var result = await _rustFSUtil.CreateBucketAsync(request.BucketName);

            if (result)
            {
                _logger.LogInformation($"管理员创建存储桶成功: {request.BucketName}");
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"存储桶 {request.BucketName} 创建成功",
                    Data = new { BucketName = request.BucketName }
                });
            }
            else
            {
                return StatusCode(500, new AdminResponseDto
                {
                    Success = false,
                    Message = "创建存储桶失败，请查看日志了解详情"
                });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"创建存储桶异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"创建存储桶失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 删除存储桶
    /// </summary>
    [HttpPost("buckets/delete")]
    [AdminApiKey]
    public async Task<IActionResult> DeleteBucket([FromBody] CreateBucketRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.BucketName))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称不能为空"
                });
            }

            var result = await _rustFSUtil.DeleteBucketAsync(request.BucketName);

            if (result)
            {
                _logger.LogInformation($"管理员删除存储桶成功: {request.BucketName}");
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"存储桶 {request.BucketName} 删除成功"
                });
            }
            else
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "删除存储桶失败，存储桶可能不存在或不为空"
                });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"删除存储桶异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"删除存储桶失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 列出所有存储桶
    /// </summary>
    [HttpGet("buckets/list")]
    [AdminApiKey]
    public async Task<IActionResult> ListBuckets()
    {
        try
        {
            var buckets = await _rustFSUtil.ListBucketsAsync();

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "获取存储桶列表成功",
                Data = new { Buckets = buckets, Count = buckets.Count }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出存储桶异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取存储桶列表失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 删除文件
    /// </summary>
    [HttpPost("files/delete")]
    [AdminApiKey]
    public async Task<IActionResult> DeleteFile([FromBody] DeleteFileRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.FileKey))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "文件Key不能为空"
                });
            }

            bool result;
            if (!string.IsNullOrWhiteSpace(request.BucketName))
            {
                result = await _rustFSUtil.DeleteFileAsync(request.FileKey, request.BucketName);
            }
            else
            {
                result = await _rustFSUtil.DeleteFileAsync(request.FileKey);
            }

            if (result)
            {
                _logger.LogInformation($"管理员删除文件成功: {request.FileKey}");
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"文件 {request.FileKey} 删除成功"
                });
            }
            else
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "删除文件失败，文件可能不存在"
                });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"删除文件异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"删除文件失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 验证存储桶名称格式
    /// </summary>
    private static bool IsValidBucketName(string bucketName)
    {
        // S3存储桶命名规则：
        // - 3-63个字符
        // - 只能包含小写字母、数字、点(.)和连字符(-)
        // - 必须以字母或数字开头和结尾
        // - 不能是IP地址格式
        if (string.IsNullOrWhiteSpace(bucketName) || bucketName.Length < 3 || bucketName.Length > 63)
        {
            return false;
        }

        if (!char.IsLetterOrDigit(bucketName[0]) || !char.IsLetterOrDigit(bucketName[^1]))
        {
            return false;
        }

        foreach (var c in bucketName)
        {
            if (!char.IsLower(c) && !char.IsDigit(c) && c != '.' && c != '-')
            {
                return false;
            }
        }

        // 不能是IP地址格式（简单检查）
        if (System.Net.IPAddress.TryParse(bucketName, out _))
        {
            return false;
        }

        return true;
    }
}

