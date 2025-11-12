using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace FileService.Repositories.Entities;

/// <summary>
/// 上传文件记录实体，用于文件去重和管理
/// </summary>
[Table("uploaded_files")]
[Index(nameof(FileHash), IsUnique = true, Name = "IX_UploadedFiles_FileHash_Unique")]
[Index(nameof(ServiceId), nameof(BucketId), Name = "IX_UploadedFiles_ServiceId_BucketId")]
[Index(nameof(ContentType), Name = "IX_UploadedFiles_ContentType")]
[Index(nameof(CreateTime), Name = "IX_UploadedFiles_CreateTime")]
[Index(nameof(ReferenceCount), Name = "IX_UploadedFiles_ReferenceCount")]
[Index(nameof(UploaderId), Name = "IX_UploadedFiles_UploaderId")]
[Index(nameof(Deleted), Name = "IX_UploadedFiles_Deleted")]
[Index(nameof(Deleted), nameof(UploaderId), Name = "IX_UploadedFiles_Deleted_UploaderId")]
[Index(nameof(ContentType), nameof(CreateTime), nameof(Id), Name = "IX_UploadedFiles_ContentType_CreateTime_Id_Desc")]
[Index(nameof(UploaderId), nameof(CreateTime), nameof(Id), Name = "IX_UploadedFiles_UploaderId_CreateTime_Id_Desc")]
public class UploadedFile
{
    /// <summary>
    /// 主键，UUID
    /// </summary>
    [Key]
    [Comment("主键ID")]
    public Guid Id { get; set; }
    
    /// <summary>
    /// 文件内容哈希值（SHA256），用于去重
    /// </summary>
    [Required]
    [MaxLength(64)]
    [Comment("文件SHA256哈希值，用于去重")]
    public string FileHash { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件在存储系统中的Key/路径
    /// </summary>
    [Required]
    [MaxLength(500)]
    [Comment("文件存储路径")]
    public string FileKey { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件访问URL
    /// </summary>
    [Required]
    [MaxLength(1000)]
    [Comment("文件访问URL")]
    public string FileUrl { get; set; } = string.Empty;
    
    /// <summary>
    /// 原始文件名称
    /// </summary>
    [MaxLength(255)]
    [Comment("原始文件名称")]
    public string OriginalFileName { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件大小（字节）
    /// </summary>
    [Comment("文件大小（字节）")]
    public long FileSize { get; set; }
    
    /// <summary>
    /// 文件MIME类型
    /// </summary>
    [Required]
    [MaxLength(100)]
    [Comment("文件MIME类型")]
    public string ContentType { get; set; } = string.Empty;
    
    /// <summary>
    /// 文件扩展名
    /// </summary>
    [MaxLength(20)]
    [Comment("文件扩展名")]
    public string FileExtension { get; set; } = string.Empty;
    
    /// <summary>
    /// 所属服务ID（外键）
    /// </summary>
    [Required]
    [Comment("所属服务ID")]
    public Guid ServiceId { get; set; }
    
    /// <summary>
    /// 所属服务（导航属性）
    /// </summary>
    [ForeignKey(nameof(ServiceId))]
    public Service Service { get; set; } = null!;
    
    /// <summary>
    /// 所属存储桶ID（外键）
    /// </summary>
    [Required]
    [Comment("所属存储桶ID")]
    public Guid BucketId { get; set; }
    
    /// <summary>
    /// 所属存储桶（导航属性）
    /// </summary>
    [ForeignKey(nameof(BucketId))]
    public Bucket Bucket { get; set; } = null!;
    
    /// <summary>
    /// 引用计数（有多少地方使用了这个文件）
    /// </summary>
    [Comment("文件引用计数")]
    public int ReferenceCount { get; set; } = 1;
    
    /// <summary>
    /// 上传者ID（可选）
    /// </summary>
    [MaxLength(450)]
    [Comment("上传者用户ID")]
    public string? UploaderId { get; set; }
    
    /// <summary>
    /// 创建时间
    /// </summary>
    [Comment("创建时间")]
    public DateTimeOffset CreateTime { get; set; }
    
    /// <summary>
    /// 最后访问时间
    /// </summary>
    [Comment("最后访问时间")]
    public DateTimeOffset LastAccessTime { get; set; }
    
    /// <summary>
    /// 是否已删除（逻辑删除标志）
    /// </summary>
    [Comment("逻辑删除标志")]
    public bool Deleted { get; set; } = false;
}

