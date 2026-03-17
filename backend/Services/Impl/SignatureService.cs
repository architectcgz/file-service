using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using FileService.Models.Dto;
using FileService.Repositories;
using FileService.Repositories.Entities;
using FileService.Services.Interfaces;

namespace FileService.Services.Impl;

/// <summary>
/// 签名管理服务实现
/// </summary>
public class SignatureService : ISignatureService
{
    private readonly FileServiceDbContext _dbContext;
    private readonly ILogger<SignatureService> _logger;
    private readonly IMemoryCache _cache;
    private const string CacheKeyPrefix = "ApiSignature_";

    public SignatureService(
        FileServiceDbContext dbContext,
        ILogger<SignatureService> logger,
        IMemoryCache cache)
    {
        _dbContext = dbContext;
        _logger = logger;
        _cache = cache;
    }

    /// <summary>
    /// 颁发新签名
    /// </summary>
    public async Task<SignatureIssueResponseDto> IssueSignatureAsync(
        SignatureIssueRequestDto request,
        string? creatorIp)
    {
        try
        {
            // 生成唯一签名Token
            var signatureToken = GenerateSignatureToken();

            var expiresAt = DateTime.UtcNow.AddMinutes(request.ExpiryMinutes);

            var signature = new ApiSignature
            {
                SignatureToken = signatureToken,
                CallerService = request.CallerService,
                CallerServiceId = request.CallerServiceId,
                AllowedOperation = request.AllowedOperation,
                AllowedFileTypes = request.AllowedFileTypes,
                MaxFileSize = request.MaxFileSize,
                Status = SignatureStatus.Active,
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = expiresAt,
                MaxUsageCount = request.MaxUsageCount,
                Notes = request.Notes,
                CreatorIp = creatorIp
            };

            _dbContext.ApiSignatures.Add(signature);
            await _dbContext.SaveChangesAsync();

            _logger.LogInformation(
                "签名颁发成功 - Token: {Token}, Service: {Service}, Operation: {Operation}, ExpiresAt: {ExpiresAt}",
                signatureToken, request.CallerService, request.AllowedOperation, expiresAt);

            return new SignatureIssueResponseDto
            {
                Success = true,
                Message = "签名颁发成功",
                SignatureToken = signatureToken,
                ExpiresAt = expiresAt,
                SignatureId = signature.Id
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "签名颁发失败");
            return new SignatureIssueResponseDto
            {
                Success = false,
                Message = $"签名颁发失败: {ex.Message}"
            };
        }
    }

    /// <summary>
    /// 验证签名
    /// </summary>
    public async Task<SignatureValidationResultDto> ValidateSignatureAsync(
        string signatureToken,
        string operation,
        string? fileType = null)
    {
        try
        {
            // 尝试从缓存获取
            var cacheKey = $"{CacheKeyPrefix}{signatureToken}";
            var signature = await _cache.GetOrCreateAsync(cacheKey, async entry =>
            {
                var sig = await _dbContext.ApiSignatures
                    .AsNoTracking()
                    .FirstOrDefaultAsync(s => s.SignatureToken == signatureToken);

                if (sig != null)
                {
                    // 设置缓存过期时间：取签名过期时间或5分钟，以较短的为准
                    var cacheExpiry = sig.ExpiresAt > DateTime.UtcNow
                        ? TimeSpan.FromMinutes(Math.Min((sig.ExpiresAt - DateTime.UtcNow).TotalMinutes, 5))
                        : TimeSpan.FromSeconds(30); // 已过期的签名只缓存30秒

                    entry.SetAbsoluteExpiration(cacheExpiry);
                }
                else
                {
                    // 不存在的签名缓存1分钟，避免重复查询
                    entry.SetAbsoluteExpiration(TimeSpan.FromMinutes(1));
                }

                return sig;
            });

            if (signature == null)
            {
                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = "签名不存在"
                };
            }

            // 检查签名是否过期
            if (signature.ExpiresAt <= DateTime.UtcNow)
            {
                // 自动标记为过期（使用ExecuteUpdate避免跟踪冲突）
                if (signature.Status == SignatureStatus.Active)
                {
                    await _dbContext.ApiSignatures
                        .Where(s => s.SignatureToken == signatureToken && s.Status == SignatureStatus.Active)
                        .ExecuteUpdateAsync(setters => setters
                            .SetProperty(s => s.Status, SignatureStatus.Expired));
                }

                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = "签名已过期"
                };
            }

            // 检查签名状态
            if (signature.Status != SignatureStatus.Active)
            {
                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = $"签名状态无效: {signature.Status}"
                };
            }

            // 检查使用次数限制
            if (signature.MaxUsageCount > 0 && signature.UsageCount >= signature.MaxUsageCount)
            {
                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = "签名使用次数已达上限"
                };
            }

            // 检查操作类型
            if (!signature.IsOperationAllowed(operation))
            {
                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = $"不允许的操作类型: {operation}"
                };
            }

            // 检查文件类型（如果指定）
            if (!string.IsNullOrEmpty(fileType) && !signature.IsFileTypeAllowed(fileType))
            {
                return new SignatureValidationResultDto
                {
                    IsValid = false,
                    Message = $"不允许的文件类型: {fileType}"
                };
            }

            return new SignatureValidationResultDto
            {
                IsValid = true,
                Message = "签名验证通过",
                SignatureId = signature.Id,
                CallerService = signature.CallerService,
                AllowedOperation = signature.AllowedOperation,
                MaxFileSize = signature.MaxFileSize
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "签名验证失败 - Token: {Token}", signatureToken);
            return new SignatureValidationResultDto
            {
                IsValid = false,
                Message = $"签名验证失败: {ex.Message}"
            };
        }
    }

    /// <summary>
    /// 记录签名使用
    /// </summary>
    public async Task RecordSignatureUsageAsync(string signatureToken)
    {
        try
        {
            // 使用ExecuteUpdate直接更新，避免实体跟踪冲突
            var now = DateTime.UtcNow;
            var affectedRows = await _dbContext.ApiSignatures
                .Where(s => s.SignatureToken == signatureToken)
                .ExecuteUpdateAsync(setters => setters
                    .SetProperty(s => s.UsageCount, s => s.UsageCount + 1)
                    .SetProperty(s => s.LastUsedAt, now));

            if (affectedRows > 0)
            {
                // 清除缓存，使下次查询获取最新数据
                _cache.Remove($"{CacheKeyPrefix}{signatureToken}");
                
                _logger.LogDebug(
                    "签名使用记录 - Token: {Token}",
                    signatureToken);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "记录签名使用失败 - Token: {Token}", signatureToken);
        }
    }

    /// <summary>
    /// 手动撤销签名
    /// </summary>
    public async Task<bool> RevokeSignatureAsync(string signatureToken, string reason)
    {
        try
        {
            var signature = await _dbContext.ApiSignatures
                .FirstOrDefaultAsync(s => s.SignatureToken == signatureToken);

            if (signature == null)
            {
                _logger.LogWarning("尝试撤销不存在的签名 - Token: {Token}", signatureToken);
                return false;
            }

            signature.Status = SignatureStatus.Revoked;
            signature.RevokedAt = DateTime.UtcNow;
            signature.RevokeReason = reason;

            await _dbContext.SaveChangesAsync();

            // 清除缓存
            _cache.Remove($"{CacheKeyPrefix}{signatureToken}");

            _logger.LogInformation(
                "签名已撤销 - Token: {Token}, Reason: {Reason}",
                signatureToken, reason);

            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "撤销签名失败 - Token: {Token}", signatureToken);
            return false;
        }
    }

    /// <summary>
    /// 批量撤销签名
    /// </summary>
    public async Task<int> RevokeBatchSignaturesAsync(List<string> signatureTokens, string reason)
    {
        try
        {
            var signatures = await _dbContext.ApiSignatures
                .Where(s => signatureTokens.Contains(s.SignatureToken))
                .ToListAsync();

            var revokedCount = 0;
            foreach (var signature in signatures)
            {
                signature.Status = SignatureStatus.Revoked;
                signature.RevokedAt = DateTime.UtcNow;
                signature.RevokeReason = reason;
                revokedCount++;
            }

            await _dbContext.SaveChangesAsync();

            // 批量清除缓存
            foreach (var token in signatureTokens)
            {
                _cache.Remove($"{CacheKeyPrefix}{token}");
            }

            _logger.LogInformation(
                "批量撤销签名完成 - 数量: {Count}, Reason: {Reason}",
                revokedCount, reason);

            return revokedCount;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "批量撤销签名失败");
            return 0;
        }
    }

    /// <summary>
    /// 清理过期签名
    /// </summary>
    public async Task<int> CleanExpiredSignaturesAsync()
    {
        try
        {
            var now = DateTime.UtcNow;
            var expiredSignatures = await _dbContext.ApiSignatures
                .Where(s => s.Status == SignatureStatus.Active && s.ExpiresAt <= now)
                .ToListAsync();

            foreach (var signature in expiredSignatures)
            {
                signature.Status = SignatureStatus.Expired;
            }

            await _dbContext.SaveChangesAsync();

            _logger.LogInformation("清理过期签名完成 - 数量: {Count}", expiredSignatures.Count);

            return expiredSignatures.Count;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "清理过期签名失败");
            return 0;
        }
    }

    /// <summary>
    /// 获取签名列表
    /// </summary>
    public async Task<PagedResultDto<ApiSignature>> GetSignaturesAsync(SignatureQueryDto query)
    {
        try
        {
            var queryable = _dbContext.ApiSignatures.AsQueryable();

            // 应用筛选条件
            if (!string.IsNullOrEmpty(query.CallerService))
            {
                queryable = queryable.Where(s => s.CallerService.Contains(query.CallerService));
            }

            if (!string.IsNullOrEmpty(query.Status))
            {
                queryable = queryable.Where(s => s.Status == query.Status);
            }

            if (!string.IsNullOrEmpty(query.Operation))
            {
                queryable = queryable.Where(s => s.AllowedOperation == query.Operation);
            }

            if (query.StartDate.HasValue)
            {
                queryable = queryable.Where(s => s.CreatedAt >= query.StartDate.Value);
            }

            if (query.EndDate.HasValue)
            {
                queryable = queryable.Where(s => s.CreatedAt <= query.EndDate.Value);
            }

            // 获取总数
            var totalCount = await queryable.CountAsync();

            // 分页
            var items = await queryable
                .OrderByDescending(s => s.CreatedAt)
                .Skip((query.PageIndex - 1) * query.PageSize)
                .Take(query.PageSize)
                .ToListAsync();

            return new PagedResultDto<ApiSignature>
            {
                Items = items,
                TotalCount = totalCount,
                PageIndex = query.PageIndex,
                PageSize = query.PageSize
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "获取签名列表失败");
            return new PagedResultDto<ApiSignature>();
        }
    }

    /// <summary>
    /// 获取签名详情
    /// </summary>
    public async Task<ApiSignature?> GetSignatureAsync(string signatureToken)
    {
        try
        {
            return await _dbContext.ApiSignatures
                .FirstOrDefaultAsync(s => s.SignatureToken == signatureToken);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "获取签名详情失败 - Token: {Token}", signatureToken);
            return null;
        }
    }

    /// <summary>
    /// 获取签名统计信息
    /// </summary>
    public async Task<SignatureStatisticsDto> GetStatisticsAsync()
    {
        try
        {
            var now = DateTime.UtcNow;
            var todayStart = now.Date;

            var statistics = new SignatureStatisticsDto
            {
                TotalSignatures = await _dbContext.ApiSignatures.CountAsync(),
                ActiveSignatures = await _dbContext.ApiSignatures
                    .CountAsync(s => s.Status == SignatureStatus.Active && s.ExpiresAt > now),
                ExpiredSignatures = await _dbContext.ApiSignatures
                    .CountAsync(s => s.Status == SignatureStatus.Expired || 
                                   (s.Status == SignatureStatus.Active && s.ExpiresAt <= now)),
                RevokedSignatures = await _dbContext.ApiSignatures
                    .CountAsync(s => s.Status == SignatureStatus.Revoked),
                TodayIssued = await _dbContext.ApiSignatures
                    .CountAsync(s => s.CreatedAt >= todayStart),
                TodayUsed = await _dbContext.ApiSignatures
                    .CountAsync(s => s.LastUsedAt >= todayStart)
            };

            // 按服务统计
            statistics.SignaturesByService = await _dbContext.ApiSignatures
                .GroupBy(s => s.CallerService)
                .Select(g => new { Service = g.Key, Count = g.Count() })
                .ToDictionaryAsync(x => x.Service, x => x.Count);

            // 按操作类型统计
            statistics.SignaturesByOperation = await _dbContext.ApiSignatures
                .GroupBy(s => s.AllowedOperation)
                .Select(g => new { Operation = g.Key, Count = g.Count() })
                .ToDictionaryAsync(x => x.Operation, x => x.Count);

            return statistics;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "获取签名统计信息失败");
            return new SignatureStatisticsDto();
        }
    }

    /// <summary>
    /// 更新签名过期时间
    /// </summary>
    public async Task<bool> UpdateExpiryTimeAsync(string signatureToken, DateTime newExpiryTime)
    {
        try
        {
            var signature = await _dbContext.ApiSignatures
                .FirstOrDefaultAsync(s => s.SignatureToken == signatureToken);

            if (signature == null)
            {
                return false;
            }

            signature.ExpiresAt = newExpiryTime;
            await _dbContext.SaveChangesAsync();

            // 清除缓存
            _cache.Remove($"{CacheKeyPrefix}{signatureToken}");

            _logger.LogInformation(
                "签名过期时间已更新 - Token: {Token}, NewExpiryTime: {Time}",
                signatureToken, newExpiryTime);

            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新签名过期时间失败 - Token: {Token}", signatureToken);
            return false;
        }
    }

    /// <summary>
    /// 生成签名Token（使用加密安全的随机数生成器）
    /// </summary>
    private static string GenerateSignatureToken()
    {
        var bytes = new byte[32];
        using (var rng = RandomNumberGenerator.Create())
        {
            rng.GetBytes(bytes);
        }
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .Replace("=", "");
    }
}
