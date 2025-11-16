using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace FileService.Repositories.Entities;

/// <summary>
/// 上传文件记录实体，用于文件去重和管理（极简版）
/// </summary>
[Table("uploaded_files")]
[Index(nameof(FileHash), IsUnique = true, Name = "IX_UploadedFiles_FileHash_Unique")]
[Index(nameof(UploaderId), Name = "IX_UploadedFiles_UploaderId")]
[Index(nameof(UploadStatus), Name = "IX_UploadedFiles_UploadStatus")]
[Index(nameof(CreateTime), Name = "IX_UploadedFiles_CreateTime")]
[Index(nameof(Deleted), Name = "IX_UploadedFiles_Deleted")]
[Index(nameof(UploaderId), nameof(Deleted), nameof(CreateTime), Name = "IX_UploadedFiles_UploaderId_Deleted_CreateTime")]
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
    /// 上传状态：0-上传中，1-上传成功，2-上传失败
    /// </summary>
    [Comment("上传状态")]
    public int UploadStatus { get; set; } = 0;
    
    /// <summary>
    /// 存储桶名称（用于多bucket场景的内部路由）
    /// </summary>
    [Required]
    [MaxLength(100)]
    [Comment("存储桶名称")]
    public string BucketName { get; set; } = string.Empty;
    
    /// <summary>
    /// 是否已删除（逻辑删除标志）
    /// </summary>
    [Comment("逻辑删除标志")]
    public bool Deleted { get; set; } = false;
}

