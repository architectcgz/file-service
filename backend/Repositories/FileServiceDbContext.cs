using Microsoft.EntityFrameworkCore;
using FileService.Repositories.Entities;

namespace FileService.Repositories;

public class FileServiceDbContext(DbContextOptions<FileServiceDbContext> options) : DbContext(options)
{
    public DbSet<Service> Services { get; set; }
    public DbSet<Bucket> Buckets { get; set; }
    public DbSet<UploadedFile> UploadedFiles { get; set; }

    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        base.OnConfiguring(optionsBuilder);
        optionsBuilder.UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // 服务表配置
        modelBuilder.Entity<Service>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnType("uuid")
                .HasDefaultValueSql("gen_random_uuid()")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.Name)
                .HasMaxLength(100)
                .IsRequired();
            
            entity.Property(e => e.Description)
                .HasMaxLength(500);
            
            entity.Property(e => e.CreateTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.UpdateTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.IsEnabled)
                .HasDefaultValue(true);
            
            entity.HasIndex(e => e.Name)
                .IsUnique()
                .HasDatabaseName("IX_Services_Name_Unique");
            
            // 一对多关系：一个服务可以有多个存储桶
            entity.HasMany(e => e.Buckets)
                .WithOne(e => e.Service)
                .HasForeignKey(e => e.ServiceId)
                .OnDelete(DeleteBehavior.Restrict);
        });
        
        // 存储桶表配置
        modelBuilder.Entity<Bucket>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnType("uuid")
                .HasDefaultValueSql("gen_random_uuid()")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.Name)
                .HasMaxLength(100)
                .IsRequired();
            
            entity.Property(e => e.Description)
                .HasMaxLength(500);
            
            entity.Property(e => e.CreateTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.UpdateTime)
                .HasDefaultValueSql("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'")
                .ValueGeneratedOnAdd();
            
            entity.Property(e => e.IsEnabled)
                .HasDefaultValue(true);
            
            entity.HasIndex(e => e.Name)
                .IsUnique()
                .HasDatabaseName("IX_Buckets_Name_Unique");
            
            entity.HasIndex(e => e.ServiceId)
                .HasDatabaseName("IX_Buckets_ServiceId");
            
            // 一对多关系：一个存储桶可以有多个文件
            entity.HasMany(e => e.UploadedFiles)
                .WithOne(e => e.Bucket)
                .HasForeignKey(e => e.BucketId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // 上传文件记录配置
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
                
            entity.Property(e => e.OriginalFileName)
                .HasMaxLength(255);
                
            entity.Property(e => e.ContentType)
                .HasMaxLength(100)
                .IsRequired();
                
            entity.Property(e => e.FileExtension)
                .HasMaxLength(20);
                
            entity.Property(e => e.UploaderId)
                .HasMaxLength(450);
            
            // 外键关系
            entity.HasOne(e => e.Service)
                .WithMany()
                .HasForeignKey(e => e.ServiceId)
                .OnDelete(DeleteBehavior.Restrict);
            
            entity.HasOne(e => e.Bucket)
                .WithMany(e => e.UploadedFiles)
                .HasForeignKey(e => e.BucketId)
                .OnDelete(DeleteBehavior.Restrict);
            
            // 唯一索引 - 文件哈希唯一性检查，用于去重上传
            entity.HasIndex(e => e.FileHash)
                .IsUnique()
                .HasFilter("\"deleted\" = false") // 只对未删除的文件保证唯一性
                .HasDatabaseName("IX_UploadedFiles_FileHash_Unique");
            
            entity.HasIndex(e => e.ContentType)
                .HasDatabaseName("IX_UploadedFiles_ContentType");
                
            entity.HasIndex(e => e.CreateTime)
                .HasDatabaseName("IX_UploadedFiles_CreateTime");
                
            entity.HasIndex(e => e.ReferenceCount)
                .HasDatabaseName("IX_UploadedFiles_ReferenceCount");
                
            entity.HasIndex(e => e.UploaderId)
                .HasDatabaseName("IX_UploadedFiles_UploaderId");
            
            entity.HasIndex(e => new { e.ServiceId, e.BucketId })
                .HasDatabaseName("IX_UploadedFiles_ServiceId_BucketId");
            
            // 为逻辑删除字段建立索引
            entity.HasIndex(e => e.Deleted)
                .HasDatabaseName("IX_UploadedFiles_Deleted");
                
            entity.HasIndex(e => new { e.Deleted, e.UploaderId })
                .HasDatabaseName("IX_UploadedFiles_Deleted_UploaderId");
            
            // 配置索引优化查询
            entity.HasIndex(e => new { e.ContentType, e.CreateTime, e.Id })
                .IsDescending(false, true, true) // 时间、ID降序
                .HasDatabaseName("IX_UploadedFiles_ContentType_CreateTime_Id_Desc");
                
            entity.HasIndex(e => new { e.UploaderId, e.CreateTime, e.Id })
                .IsDescending(false, true, true) // 时间、ID降序
                .HasDatabaseName("IX_UploadedFiles_UploaderId_CreateTime_Id_Desc");
            
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
}

