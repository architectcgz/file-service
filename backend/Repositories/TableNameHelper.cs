namespace FileService.Repositories;

/// <summary>
/// 表名辅助类，用于根据服务名生成表名
/// </summary>
public static class TableNameHelper
{
    /// <summary>
    /// 基础表名
    /// </summary>
    private const string BaseTableName = "uploaded_files";

    /// <summary>
    /// 根据服务名生成表名
    /// </summary>
    /// <param name="service">服务名，如果为空或 null，返回基础表名</param>
    /// <returns>表名</returns>
    public static string GetTableName(string? service)
    {
        if (string.IsNullOrWhiteSpace(service))
        {
            return BaseTableName;
        }

        // 清理服务名，只保留字母、数字和下划线
        var sanitizedService = SanitizeServiceName(service);
        return $"{BaseTableName}_{sanitizedService}";
    }

    /// <summary>
    /// 清理服务名，确保表名安全
    /// </summary>
    /// <param name="service">原始服务名</param>
    /// <returns>清理后的服务名</returns>
    private static string SanitizeServiceName(string service)
    {
        // 只保留字母、数字和下划线，转换为小写
        return new string(service
            .Where(c => char.IsLetterOrDigit(c) || c == '_')
            .ToArray())
            .ToLowerInvariant();
    }

    /// <summary>
    /// 获取所有可能的表名（用于迁移或查询）
    /// </summary>
    /// <param name="services">服务名列表</param>
    /// <returns>表名列表</returns>
    public static IEnumerable<string> GetAllTableNames(IEnumerable<string> services)
    {
        yield return BaseTableName; // 基础表（空服务名）
        
        foreach (var service in services.Where(s => !string.IsNullOrWhiteSpace(s)))
        {
            yield return GetTableName(service);
        }
    }
}

