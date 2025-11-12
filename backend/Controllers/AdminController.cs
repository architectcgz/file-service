using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using FileService.Attributes;
using FileService.Config;
using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Repositories.Entities;
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
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_service_id_bucket_id ON {tableName} (service_id, bucket_id);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_deleted_uploader_id ON {tableName} (deleted, uploader_id);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_content_type_create_time_id ON {tableName} (content_type, create_time DESC, id DESC);
                    CREATE INDEX IF NOT EXISTS ix_{tableName}_uploader_id_create_time_id ON {tableName} (uploader_id, create_time DESC, id DESC);
                ";

                await _dbContext.Database.ExecuteSqlRawAsync(createTableSql);

                _logger.LogInformation($"管理员创建存储表成功: {tableName}, 服务: {request.Service}");

                // 检查服务是否已存在于services表中
                var sanitizedServiceName = TableNameHelper.SanitizeServiceName(request.Service);
                var existingService = await _dbContext.Services.FirstOrDefaultAsync(s => s.Name == sanitizedServiceName);
                
                if (existingService == null)
                {
                    // 创建服务记录
                    var service = new Service
                    {
                        Id = Guid.NewGuid(),
                        Name = sanitizedServiceName,
                        Description = $"通过创建表自动创建的服务",
                        CreateTime = DateTimeOffset.UtcNow,
                        UpdateTime = DateTimeOffset.UtcNow,
                        IsEnabled = true
                    };
                    _dbContext.Services.Add(service);
                    await _dbContext.SaveChangesAsync();
                    _logger.LogInformation($"自动创建服务记录: {sanitizedServiceName}, ID: {service.Id}");
                }

                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"表 {tableName} 创建成功，服务 {sanitizedServiceName} 已记录",
                    Data = new { TableName = tableName, Service = sanitizedServiceName }
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
                _logger.LogInformation($"管理员在RustFS创建存储桶成功: {request.BucketName}");
                
                // 检查存储桶是否已存在于buckets表中
                var sanitizedBucketName = TableNameHelper.SanitizeServiceName(request.BucketName);
                var existingBucket = await _dbContext.Buckets.FirstOrDefaultAsync(b => b.Name == sanitizedBucketName);
                
                if (existingBucket == null)
                {
                    // 获取或创建默认服务
                    Guid serviceId;
                    if (request.ServiceId.HasValue)
                    {
                        // 使用提供的服务ID
                        var service = await _dbContext.Services.FindAsync(request.ServiceId.Value);
                        if (service == null)
                        {
                            return BadRequest(new AdminResponseDto
                            {
                                Success = false,
                                Message = $"服务ID {request.ServiceId.Value} 不存在"
                            });
                        }
                        serviceId = request.ServiceId.Value;
                    }
                    else
                    {
                        // 使用默认服务
                        var defaultService = await _dbContext.Services.FirstOrDefaultAsync(s => s.Name == "default");
                        if (defaultService == null)
                        {
                            _logger.LogInformation("创建默认服务...");
                            defaultService = new Service
                            {
                                Id = Guid.NewGuid(),
                                Name = "default",
                                Description = "默认服务，用于存放通过创建存储桶接口创建的存储桶",
                                CreateTime = DateTimeOffset.UtcNow,
                                UpdateTime = DateTimeOffset.UtcNow,
                                IsEnabled = true
                            };
                            _dbContext.Services.Add(defaultService);
                            await _dbContext.SaveChangesAsync();
                            _logger.LogInformation($"默认服务创建成功，ID: {defaultService.Id}");
                        }
                        serviceId = defaultService.Id;
                    }
                    
                    // 创建存储桶记录
                    var bucket = new Bucket
                    {
                        Id = Guid.NewGuid(),
                        Name = sanitizedBucketName,
                        Description = request.Description ?? $"通过创建存储桶接口创建",
                        ServiceId = serviceId,
                        CreateTime = DateTimeOffset.UtcNow,
                        UpdateTime = DateTimeOffset.UtcNow,
                        IsEnabled = true
                    };
                    _dbContext.Buckets.Add(bucket);
                    await _dbContext.SaveChangesAsync();
                    _logger.LogInformation($"自动创建存储桶记录: {sanitizedBucketName}, ID: {bucket.Id}, ServiceId: {serviceId}");
                }
                
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"存储桶 {sanitizedBucketName} 创建成功并已记录",
                    Data = new { BucketName = sanitizedBucketName }
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
    /// 列出指定存储桶中的所有文件夹
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    [HttpGet("buckets/{bucketName}/folders")]
    [AdminApiKey]
    public async Task<IActionResult> ListFolders(string bucketName)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(bucketName))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称不能为空"
                });
            }
            
            var folders = await _rustFSUtil.ListFoldersAsync(bucketName);

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = $"获取存储桶 '{bucketName}' 的文件夹列表成功",
                Data = new { Folders = folders, Count = folders.Count }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出存储桶 '{bucketName}' 的文件夹异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取文件夹列表失败: {ex.Message}"
            });
        }
    }
    
    /// <summary>
    /// 列出指定文件夹下的文件
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <param name="folder">文件夹路径</param>
    [HttpGet("buckets/{bucketName}/folders/{folder}/files")]
    [AdminApiKey]
    public async Task<IActionResult> ListFilesInFolder(string bucketName, string folder)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(bucketName))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称不能为空"
                });
            }
            
            var files = await _rustFSUtil.ListFilesInFolderAsync(bucketName, folder ?? "");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = $"获取文件夹 '{folder}' 的文件列表成功",
                Data = new { Files = files, Count = files.Count }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"列出文件夹 '{folder}' 的文件异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取文件列表失败: {ex.Message}"
            });
        }
    }
    
    /// <summary>
    /// 修复文件的Content-Type元数据
    /// </summary>
    /// <param name="bucketName">存储桶名称</param>
    /// <param name="fileKey">文件Key</param>
    [HttpPost("buckets/{bucketName}/files/{fileKey}/fix-content-type")]
    [AdminApiKey]
    public async Task<IActionResult> FixFileContentType(string bucketName, string fileKey)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(bucketName) || string.IsNullOrWhiteSpace(fileKey))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶名称和文件Key不能为空"
                });
            }
            
            var result = await _rustFSUtil.FixFileContentTypeAsync(bucketName, fileKey);

            if (result)
            {
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = $"文件 '{fileKey}' 的Content-Type已修复"
                });
            }
            else
            {
                return StatusCode(500, new AdminResponseDto
                {
                    Success = false,
                    Message = "修复文件Content-Type失败"
                });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"修复文件Content-Type异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"修复失败: {ex.Message}"
            });
        }
    }
    
    /// <summary>
    /// 同步 RustFS 中的存储桶到数据库
    /// 将 RustFS/MinIO 中已存在但数据库中没有的存储桶创建到数据库中
    /// </summary>
    [HttpPost("buckets/sync")]
    [AdminApiKey]
    public async Task<IActionResult> SyncBuckets()
    {
        try
        {
            _logger.LogInformation("开始同步存储桶...");
            
            // 1. 从 RustFS 获取所有存储桶
            var rustfsBuckets = await _rustFSUtil.ListBucketsAsync();
            _logger.LogInformation($"从 RustFS 获取到 {rustfsBuckets.Count} 个存储桶");
            
            // 2. 从数据库获取已有的存储桶
            var dbBuckets = await _dbContext.Buckets.ToListAsync();
            var dbBucketNames = dbBuckets.Select(b => b.Name).ToHashSet();
            _logger.LogInformation($"数据库中已有 {dbBuckets.Count} 个存储桶记录");
            
            // 3. 找出缺失的存储桶
            var missingBuckets = rustfsBuckets.Where(b => !dbBucketNames.Contains(b)).ToList();
            _logger.LogInformation($"发现 {missingBuckets.Count} 个缺失的存储桶");
            
            if (missingBuckets.Count == 0)
            {
                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = "所有存储桶已同步，无需操作",
                    Data = new { SyncedCount = 0, TotalRustFSBuckets = rustfsBuckets.Count, TotalDBBuckets = dbBuckets.Count }
                });
            }
            
            // 4. 获取或创建默认服务
            var defaultService = await _dbContext.Services.FirstOrDefaultAsync(s => s.Name == "default");
            if (defaultService == null)
            {
                _logger.LogInformation("创建默认服务...");
                defaultService = new Service
                {
                    Id = Guid.NewGuid(),
                    Name = "default",
                    Description = "用于存放从 RustFS 同步的存储桶",
                    CreateTime = DateTimeOffset.UtcNow,
                    UpdateTime = DateTimeOffset.UtcNow,
                    IsEnabled = true
                };
                _dbContext.Services.Add(defaultService);
                await _dbContext.SaveChangesAsync();
                _logger.LogInformation($"默认服务创建成功，ID: {defaultService.Id}");
            }
            
            // 5. 为缺失的存储桶创建数据库记录
            var syncedBuckets = new List<string>();
            foreach (var bucketName in missingBuckets)
            {
                var bucket = new Bucket
                {
                    Id = Guid.NewGuid(),
                    Name = bucketName,
                    Description = $"从 RustFS 同步的存储桶",
                    ServiceId = defaultService.Id,
                    CreateTime = DateTimeOffset.UtcNow,
                    UpdateTime = DateTimeOffset.UtcNow,
                    IsEnabled = true
                };
                
                _dbContext.Buckets.Add(bucket);
                syncedBuckets.Add(bucketName);
                _logger.LogInformation($"创建存储桶记录: {bucketName}, ID: {bucket.Id}");
            }
            
            await _dbContext.SaveChangesAsync();
            _logger.LogInformation($"成功同步 {syncedBuckets.Count} 个存储桶到数据库");
            
            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = $"成功同步 {syncedBuckets.Count} 个存储桶",
                Data = new 
                { 
                    SyncedCount = syncedBuckets.Count,
                    SyncedBuckets = syncedBuckets,
                    TotalRustFSBuckets = rustfsBuckets.Count,
                    TotalDBBuckets = dbBuckets.Count + syncedBuckets.Count,
                    DefaultServiceId = defaultService.Id,
                    DefaultServiceName = defaultService.Name
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"同步存储桶异常: {ex.Message}");
            _logger.LogError($"异常详情: {ex}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"同步存储桶失败: {ex.Message}"
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
    /// 获取所有已创建的服务列表
    /// </summary>
    [HttpGet("services")]
    [AdminApiKey]
    public async Task<IActionResult> GetServices()
    {
        try
        {
            var services = await _dbContext.Services
                .Select(s => new ServiceResponseDto
                {
                    Id = s.Id,
                    Name = s.Name,
                    Description = s.Description,
                    CreateTime = s.CreateTime,
                    UpdateTime = s.UpdateTime,
                    IsEnabled = s.IsEnabled,
                    BucketCount = s.Buckets.Count
                })
                .OrderBy(s => s.Name)
                .ToListAsync();

            _logger.LogInformation($"获取服务列表成功，共 {services.Count} 个服务");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "获取服务列表成功",
                Data = services
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取服务列表异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取服务列表失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 获取所有已创建的服务列表
    /// </summary>
    [HttpGet("services/list")]
    [AdminApiKey]
    public async Task<IActionResult> ListServices()
    {
        try
        {
            var connection = _dbContext.Database.GetDbConnection();
            await connection.OpenAsync();
            try
            {
                var command = connection.CreateCommand();
                command.CommandText = @"
                    SELECT table_name 
                    FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_name LIKE 'uploaded_files_%'
                    ORDER BY table_name";

                var services = new List<string>();
                using var reader = await command.ExecuteReaderAsync();
                while (await reader.ReadAsync())
                {
                    var tableName = reader.GetString(0);
                    // 从表名提取服务名：uploaded_files_xxx -> xxx
                    if (tableName.StartsWith("uploaded_files_"))
                    {
                        var serviceName = tableName.Substring("uploaded_files_".Length);
                        services.Add(serviceName);
                    }
                }

                _logger.LogInformation($"获取服务列表成功，共 {services.Count} 个服务");

                return Ok(new AdminResponseDto
                {
                    Success = true,
                    Message = "获取服务列表成功",
                    Data = new { Services = services, Count = services.Count }
                });
            }
            finally
            {
                await connection.CloseAsync();
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取服务列表异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取服务列表失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 创建服务
    /// </summary>
    [HttpPost("services/create")]
    [AdminApiKey]
    public async Task<IActionResult> CreateService([FromBody] CreateServiceRequestDto request)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.Name))
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = "服务名称不能为空"
                });
            }

            // 检查服务名是否已存在
            var sanitizedName = TableNameHelper.SanitizeServiceName(request.Name);
            var existingService = await _dbContext.Services
                .FirstOrDefaultAsync(s => s.Name == sanitizedName);

            if (existingService != null)
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = $"服务 {sanitizedName} 已存在"
                });
            }

            var service = new Repositories.Entities.Service
            {
                Id = Guid.NewGuid(),
                Name = sanitizedName,
                Description = request.Description,
                CreateTime = DateTimeOffset.UtcNow,
                UpdateTime = DateTimeOffset.UtcNow,
                IsEnabled = true
            };

            _dbContext.Services.Add(service);
            await _dbContext.SaveChangesAsync();

            _logger.LogInformation($"管理员创建服务成功: {sanitizedName}, ID: {service.Id}");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = $"服务 {sanitizedName} 创建成功",
                Data = new ServiceResponseDto
                {
                    Id = service.Id,
                    Name = service.Name,
                    Description = service.Description,
                    CreateTime = service.CreateTime,
                    UpdateTime = service.UpdateTime,
                    IsEnabled = service.IsEnabled,
                    BucketCount = 0
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"创建服务异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"创建服务失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 创建存储桶（数据库记录）
    /// </summary>
    [HttpPost("services/{serviceId}/buckets/create")]
    [AdminApiKey]
    public async Task<IActionResult> CreateBucketInService(Guid serviceId, [FromBody] CreateBucketRequestDto request)
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

            // 检查服务是否存在
            var service = await _dbContext.Services.FindAsync(serviceId);
            if (service == null)
            {
                return NotFound(new AdminResponseDto
                {
                    Success = false,
                    Message = "服务不存在"
                });
            }

            // 检查存储桶名是否已存在
            var sanitizedName = TableNameHelper.SanitizeServiceName(request.BucketName);
            var existingBucket = await _dbContext.Buckets
                .FirstOrDefaultAsync(b => b.Name == sanitizedName && b.ServiceId == serviceId);

            if (existingBucket != null)
            {
                return BadRequest(new AdminResponseDto
                {
                    Success = false,
                    Message = $"存储桶 {sanitizedName} 在该服务下已存在"
                });
            }

            var bucket = new Repositories.Entities.Bucket
            {
                Id = Guid.NewGuid(),
                Name = sanitizedName,
                Description = request.Description,
                ServiceId = serviceId,
                CreateTime = DateTimeOffset.UtcNow,
                UpdateTime = DateTimeOffset.UtcNow,
                IsEnabled = true
            };

            _dbContext.Buckets.Add(bucket);
            await _dbContext.SaveChangesAsync();

            // 创建对应的分表
            await _dbContext.EnsureTableExistsAsync(service.Name, bucket.Name);

            _logger.LogInformation($"管理员创建存储桶成功: {sanitizedName}, ID: {bucket.Id}, ServiceId: {serviceId}");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = $"存储桶 {sanitizedName} 创建成功",
                Data = new BucketResponseDto
                {
                    Id = bucket.Id,
                    Name = bucket.Name,
                    Description = bucket.Description,
                    ServiceId = bucket.ServiceId,
                    ServiceName = service.Name,
                    CreateTime = bucket.CreateTime,
                    UpdateTime = bucket.UpdateTime,
                    IsEnabled = bucket.IsEnabled,
                    FileCount = 0
                }
            });
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
    /// 获取服务下的存储桶列表
    /// </summary>
    [HttpGet("services/{serviceId}/buckets")]
    [AdminApiKey]
    public async Task<IActionResult> GetBucketsByService(Guid serviceId)
    {
        try
        {
            var service = await _dbContext.Services.FindAsync(serviceId);
            if (service == null)
            {
                return NotFound(new AdminResponseDto
                {
                    Success = false,
                    Message = "服务不存在"
                });
            }

            var bucketsFromDb = await _dbContext.Buckets
                .Where(b => b.ServiceId == serviceId)
                .OrderBy(b => b.Name)
                .ToListAsync();

            var buckets = new List<BucketResponseDto>();
            
            foreach (var b in bucketsFromDb)
            {
                // 从 RustFS 获取实际文件数
                var fileCount = await _rustFSUtil.GetBucketFileCountAsync(b.Name);
                
                buckets.Add(new BucketResponseDto
                {
                    Id = b.Id,
                    Name = b.Name,
                    Description = b.Description,
                    ServiceId = b.ServiceId,
                    ServiceName = service.Name,
                    CreateTime = b.CreateTime,
                    UpdateTime = b.UpdateTime,
                    IsEnabled = b.IsEnabled,
                    FileCount = (int)fileCount
                });
            }

            _logger.LogInformation($"获取服务 {service.Name} 的存储桶列表成功，共 {buckets.Count} 个");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "获取存储桶列表成功",
                Data = buckets
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取存储桶列表异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取存储桶列表失败: {ex.Message}"
            });
        }
    }

    /// <summary>
    /// 获取存储桶中的文件列表
    /// </summary>
    [HttpGet("buckets/{bucketId}/files")]
    [AdminApiKey]
    public async Task<IActionResult> GetFilesByBucket(Guid bucketId, [FromQuery] int page = 1, [FromQuery] int pageSize = 20)
    {
        try
        {
            var bucket = await _dbContext.Buckets
                .Include(b => b.Service)
                .FirstOrDefaultAsync(b => b.Id == bucketId);

            if (bucket == null)
            {
                return NotFound(new AdminResponseDto
                {
                    Success = false,
                    Message = "存储桶不存在"
                });
            }

            var query = _dbContext.UploadedFiles
                .Where(f => f.BucketId == bucketId && !f.Deleted);

            var totalCount = await query.CountAsync();
            var files = await query
                .OrderByDescending(f => f.CreateTime)
                .Skip((page - 1) * pageSize)
                .Take(pageSize)
                .Select(f => new
                {
                    f.Id,
                    f.FileHash,
                    f.FileKey,
                    f.FileUrl,
                    f.OriginalFileName,
                    f.FileSize,
                    f.ContentType,
                    f.FileExtension,
                    f.ReferenceCount,
                    f.UploaderId,
                    f.CreateTime,
                    f.LastAccessTime
                })
                .ToListAsync();

            _logger.LogInformation($"获取存储桶 {bucket.Name} 的文件列表成功，共 {totalCount} 个文件");

            return Ok(new AdminResponseDto
            {
                Success = true,
                Message = "获取文件列表成功",
                Data = new
                {
                    TotalCount = totalCount,
                    Page = page,
                    PageSize = pageSize,
                    TotalPages = (int)Math.Ceiling(totalCount / (double)pageSize),
                    Files = files,
                    BucketName = bucket.Name,
                    ServiceName = bucket.Service?.Name
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"获取文件列表异常: {ex.Message}");
            return StatusCode(500, new AdminResponseDto
            {
                Success = false,
                Message = $"获取文件列表失败: {ex.Message}"
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

