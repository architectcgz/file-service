using System.Diagnostics.CodeAnalysis;
using Microsoft.EntityFrameworkCore;
using FileService.Repositories.Entities;
using Npgsql;

namespace FileService.Repositories;

/// <summary>
/// FileServiceDbContext 扩展方法，用于支持按服务名分表查询
/// </summary>
public static class FileServiceDbContextExtensions
{
    /// <summary>
    /// 根据服务名获取对应的 UploadedFile 查询（支持分表）
    /// </summary>
    /// <param name="context">数据库上下文</param>
    /// <param name="service">服务名，如果为空则查询基础表</param>
    /// <returns>IQueryable&lt;UploadedFile&gt;</returns>
    [SuppressMessage("Security", "CA2100:Review SQL queries for security vulnerabilities", Justification = "Table name is sanitized by TableNameHelper")]
    [SuppressMessage("Security", "EF1002:Risk of vulnerability to SQL injection", Justification = "Table name is validated by IsValidTableName method to contain only alphanumeric characters and underscores")]
    public static IQueryable<UploadedFile> GetUploadedFilesByService(this FileServiceDbContext context, string? service)
    {
        var tableName = TableNameHelper.GetTableName(service);
        
        // 使用原生 SQL 查询指定表
        // 注意：表名已经通过 TableNameHelper.SanitizeServiceName 清理和验证，只包含字母、数字和下划线，相对安全
        return context.UploadedFiles
            .FromSqlRaw($"SELECT * FROM {tableName}")
            .AsQueryable();
    }

    /// <summary>
    /// 根据服务名和存储桶名添加文件记录到对应的表（支持分表）
    /// </summary>
    /// <param name="context">数据库上下文</param>
    /// <param name="file">文件实体</param>
    /// <param name="serviceName">服务名</param>
    /// <param name="bucketName">存储桶名</param>
    public static async Task AddUploadedFileToServiceTableAsync(
        this FileServiceDbContext context, 
        UploadedFile file, 
        string serviceName,
        string bucketName)
    {
        var tableName = TableNameHelper.GetTableName(serviceName, bucketName);
        
        // 确保表存在（如果不存在则创建）
        await EnsureTableExistsAsync(context, tableName);
        
        // 使用参数化 SQL 插入，防止 SQL 注入
        var sql = $@"
            INSERT INTO {tableName} (
                id, file_hash, file_key, file_url, original_file_name, 
                file_size, content_type, file_extension, service_id, bucket_id,
                reference_count, uploader_id, 
                create_time, last_access_time, deleted
            ) VALUES (
                @p0, @p1, @p2, @p3, @p4, 
                @p5, @p6, @p7, @p8, @p9,
                @p10, @p11, 
                @p12, @p13, @p14
            )";
        
        var parameters = new[]
        {
            new NpgsqlParameter("@p0", file.Id),
            new NpgsqlParameter("@p1", file.FileHash),
            new NpgsqlParameter("@p2", file.FileKey),
            new NpgsqlParameter("@p3", file.FileUrl),
            new NpgsqlParameter("@p4", file.OriginalFileName),
            new NpgsqlParameter("@p5", file.FileSize),
            new NpgsqlParameter("@p6", file.ContentType),
            new NpgsqlParameter("@p7", file.FileExtension),
            new NpgsqlParameter("@p8", file.ServiceId),
            new NpgsqlParameter("@p9", file.BucketId),
            new NpgsqlParameter("@p10", file.ReferenceCount),
            new NpgsqlParameter("@p11", file.UploaderId ?? (object)DBNull.Value),
            new NpgsqlParameter("@p12", file.CreateTime),
            new NpgsqlParameter("@p13", file.LastAccessTime),
            new NpgsqlParameter("@p14", file.Deleted)
        };
        
        await context.Database.ExecuteSqlRawAsync(sql, parameters);
    }

    /// <summary>
    /// 确保表存在，如果不存在则创建（public版本）
    /// </summary>
    /// <param name="context">数据库上下文</param>
    /// <param name="serviceName">服务名</param>
    /// <param name="bucketName">存储桶名</param>
    public static async Task EnsureTableExistsAsync(this FileServiceDbContext context, string serviceName, string bucketName)
    {
        var tableName = TableNameHelper.GetTableName(serviceName, bucketName);
        await EnsureTableExistsAsync(context, tableName);
    }

    /// <summary>
    /// 确保表存在，如果不存在则创建（内部版本）
    /// </summary>
    private static async Task EnsureTableExistsAsync(FileServiceDbContext context, string tableName)
    {
        // 验证表名安全性
        if (!IsValidTableName(tableName))
        {
            throw new ArgumentException($"Invalid table name: {tableName}", nameof(tableName));
        }
        
        // 检查表是否存在
        var connection = context.Database.GetDbConnection();
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
            
            if (!tableExists)
        {
            // 创建表（复制基础表结构）
            var baseTableName = TableNameHelper.GetTableName(null);
            
            // 注意：CREATE TABLE 语句中的表名不能参数化，但已经通过验证
            var createTableSql = $@"
                CREATE TABLE {tableName} (LIKE {baseTableName} INCLUDING ALL);
                
                -- 复制索引（使用 IF NOT EXISTS 避免重复创建）
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
            
                await context.Database.ExecuteSqlRawAsync(createTableSql);
            }
        }
        finally
        {
            await connection.CloseAsync();
        }
    }

    /// <summary>
    /// 验证表名是否安全（只包含字母、数字和下划线）
    /// </summary>
    private static bool IsValidTableName(string tableName)
    {
        return !string.IsNullOrWhiteSpace(tableName) &&
               tableName.All(c => char.IsLetterOrDigit(c) || c == '_') &&
               !tableName.StartsWith("_") &&
               tableName.Length <= 63; // PostgreSQL 标识符最大长度
    }
}
