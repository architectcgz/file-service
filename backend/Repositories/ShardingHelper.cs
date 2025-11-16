namespace FileService.Repositories;

/// <summary>
/// 分表辅助类 - 按文件哈希分表
/// </summary>
public static class ShardingHelper
{
    /// <summary>
    /// 是否启用分表（默认关闭，文件量达到100万时再启用）
    /// </summary>
    public static bool EnableSharding { get; set; } = false;
    
    /// <summary>
    /// 分表数量（默认256张表，可根据实际情况调整）
    /// </summary>
    public static int ShardCount { get; set; } = 256;
    
    /// <summary>
    /// 根据文件哈希获取表名
    /// </summary>
    /// <param name="fileHash">文件SHA256哈希值</param>
    /// <returns>表名</returns>
    public static string GetTableNameByHash(string fileHash)
    {
        if (!EnableSharding || string.IsNullOrEmpty(fileHash))
        {
            return "uploaded_files";
        }
        
        // 取哈希值前2位（00-ff，共256种可能）
        var prefix = fileHash.Substring(0, 2).ToLower();
        return $"uploaded_files_{prefix}";
    }
    
    /// <summary>
    /// 获取所有分表名称
    /// </summary>
    /// <returns>分表名称列表</returns>
    public static List<string> GetAllTableNames()
    {
        if (!EnableSharding)
        {
            return new List<string> { "uploaded_files" };
        }
        
        var tables = new List<string>();
        for (int i = 0; i < ShardCount; i++)
        {
            tables.Add($"uploaded_files_{i:x2}");
        }
        return tables;
    }
    
    /// <summary>
    /// 生成创建分表的SQL
    /// </summary>
    /// <param name="tableName">分表名称</param>
    /// <returns>创建表的SQL语句</returns>
    public static string GenerateCreateTableSql(string tableName)
    {
        return $@"
CREATE TABLE IF NOT EXISTS {tableName} (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_hash VARCHAR(64) NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    reference_count INTEGER NOT NULL DEFAULT 1,
    uploader_id VARCHAR(450),
    create_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    last_access_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    upload_status INTEGER NOT NULL DEFAULT 0,
    bucket_name VARCHAR(100) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false
);

-- 创建索引
CREATE UNIQUE INDEX IF NOT EXISTS ix_{tableName}_file_hash ON {tableName} (file_hash) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS ix_{tableName}_uploader_id ON {tableName} (uploader_id);
CREATE INDEX IF NOT EXISTS ix_{tableName}_upload_status ON {tableName} (upload_status);
CREATE INDEX IF NOT EXISTS ix_{tableName}_create_time ON {tableName} (create_time DESC);
CREATE INDEX IF NOT EXISTS ix_{tableName}_deleted ON {tableName} (deleted);
";
    }
}
