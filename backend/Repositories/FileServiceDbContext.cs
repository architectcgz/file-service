using Microsoft.EntityFrameworkCore;
using FileService.Repositories.Entities;

namespace FileService.Repositories;

public class FileServiceDbContext(DbContextOptions<FileServiceDbContext> options) : DbContext(options)
{
    public DbSet<UploadedFile> UploadedFiles { get; set; }

    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        base.OnConfiguring(optionsBuilder);
        optionsBuilder.UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // 上传文件记录配置（简化版）
        modelBuilder.Entity<UploadedFile>(entity =>
        {
            // 配置ID生成策略 - 使用数据库自动生成 UUID
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnType("uuid")
                .HasDefaultValueSql("gen_random_uuid()")
                .ValueGeneratedOnAdd();
            
            // 字段约束
            entity.Property(e => e.FileHash)
                .HasMaxLength(64)
                .IsRequired();
            
            entity.Property(e => e.FileKey)
                .HasMaxLength(500)
                .IsRequired();
                
            entity.Property(e => e.FileUrl)
                .HasMaxLength(1000)
                .IsRequired();
                
            entity.Property(e => e.UploaderId)
                .HasMaxLength(450);
            
            // 唯一索引 - 文件哈希唯一性检查，用于去重上传
            entity.HasIndex(e => e.FileHash)
                .IsUnique()
                .HasFilter("\"deleted\" = false")
                .HasDatabaseName("IX_UploadedFiles_FileHash_Unique");
            
            // 核心索引
            entity.HasIndex(e => e.UploaderId)
                .HasDatabaseName("IX_UploadedFiles_UploaderId");
                
            entity.HasIndex(e => e.UploadStatus)
                .HasDatabaseName("IX_UploadedFiles_UploadStatus");
                
            entity.HasIndex(e => e.CreateTime)
                .IsDescending()
                .HasDatabaseName("IX_UploadedFiles_CreateTime");
            
            entity.HasIndex(e => e.Deleted)
                .HasDatabaseName("IX_UploadedFiles_Deleted");
                
            // 组合索引 - 查询用户的文件列表
            entity.HasIndex(e => new { e.UploaderId, e.Deleted, e.CreateTime })
                .IsDescending(false, false, true)
                .HasDatabaseName("IX_UploadedFiles_UploaderId_Deleted_CreateTime");
            
            // 时间戳默认值
            entity.Property(e => e.CreateTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
                
            entity.Property(e => e.LastAccessTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
                
            entity.Property(e => e.ReferenceCount)
                .HasDefaultValue(1);
                
            entity.Property(e => e.Deleted)
                .HasDefaultValue(false);
        });
    }
    
    /// <summary>
    /// 根据文件哈希获取对应的分表查询
    /// </summary>
    /// <param name="fileHash">文件哈希值</param>
    /// <returns>对应分表的查询</returns>
    public IQueryable<UploadedFile> GetUploadedFilesByHash(string fileHash)
    {
        var tableName = ShardingHelper.GetTableNameByHash(fileHash);
        return UploadedFiles.FromSqlRaw($"SELECT * FROM {tableName}");
    }
    
    /// <summary>
    /// 确保分表存在
    /// </summary>
    /// <param name="fileHash">文件哈希值</param>
    public async Task EnsureShardTableExistsAsync(string fileHash)
    {
        if (!ShardingHelper.EnableSharding)
        {
            return;
        }
        
        var tableName = ShardingHelper.GetTableNameByHash(fileHash);
        var sql = ShardingHelper.GenerateCreateTableSql(tableName);
        await Database.ExecuteSqlRawAsync(sql);
    }
    
    /// <summary>
    /// 添加文件记录到对应的分表
    /// </summary>
    /// <param name="file">文件实体</param>
    public async Task AddToShardTableAsync(UploadedFile file)
    {
        await EnsureShardTableExistsAsync(file.FileHash);
        
        if (ShardingHelper.EnableSharding)
        {
            var tableName = ShardingHelper.GetTableNameByHash(file.FileHash);
            var sql = $@"
                INSERT INTO {tableName} 
                (id, file_hash, file_key, file_url, reference_count, uploader_id, 
                 create_time, last_access_time, upload_status, bucket_name, deleted)
                VALUES 
                (@Id, @FileHash, @FileKey, @FileUrl, @ReferenceCount, @UploaderId, 
                 @CreateTime, @LastAccessTime, @UploadStatus, @BucketName, @Deleted)";
            
            await Database.ExecuteSqlRawAsync(sql,
                file.Id, file.FileHash, file.FileKey, file.FileUrl, file.ReferenceCount, 
                file.UploaderId, file.CreateTime, file.LastAccessTime, file.UploadStatus, file.BucketName, file.Deleted);
        }
        else
        {
            UploadedFiles.Add(file);
            await SaveChangesAsync();
        }
    }
    
    /// <summary>
    /// UPSERT文件记录到对应的分表（处理并发冲突）
    /// 如果记录已存在，则更新引用计数和访问时间
    /// </summary>
    /// <param name="file">文件实体</param>
    public async Task UpsertToShardTableAsync(UploadedFile file)
    {
        await EnsureShardTableExistsAsync(file.FileHash);
        
        if (ShardingHelper.EnableSharding)
        {
            var tableName = ShardingHelper.GetTableNameByHash(file.FileHash);
            
            // 使用 PostgreSQL 的 INSERT ... ON CONFLICT ... DO UPDATE 语法
            // 注意：对于部分唯一索引（带 WHERE 条件），需要指定列名和 WHERE 条件
            var sql = $@"
                INSERT INTO {tableName} 
                (id, file_hash, file_key, file_url, reference_count, uploader_id, 
                 create_time, last_access_time, upload_status, bucket_name, deleted)
                VALUES 
                (@p0, @p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10)
                ON CONFLICT (file_hash) WHERE deleted = false
                DO UPDATE SET 
                    reference_count = {tableName}.reference_count + 1,
                    last_access_time = EXCLUDED.last_access_time";
            
            await Database.ExecuteSqlRawAsync(sql,
                file.Id, file.FileHash, file.FileKey, file.FileUrl, file.ReferenceCount, 
                file.UploaderId, file.CreateTime, file.LastAccessTime, file.UploadStatus, file.BucketName, file.Deleted);
        }
        else
        {
            // 非分表场景：使用原生 SQL 的 ON CONFLICT
            var sql = @"
                INSERT INTO uploaded_files 
                (id, file_hash, file_key, file_url, reference_count, uploader_id, 
                 create_time, last_access_time, upload_status, bucket_name, deleted)
                VALUES 
                (@p0, @p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10)
                ON CONFLICT (file_hash) WHERE deleted = false
                DO UPDATE SET 
                    reference_count = uploaded_files.reference_count + 1,
                    last_access_time = EXCLUDED.last_access_time";
            
            await Database.ExecuteSqlRawAsync(sql,
                file.Id, file.FileHash, file.FileKey, file.FileUrl, file.ReferenceCount, 
                file.UploaderId, file.CreateTime, file.LastAccessTime, file.UploadStatus, file.BucketName, file.Deleted);
        }
    }
}

