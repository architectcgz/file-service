using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace FileService.Repositories.Entities;

/// <summary>
/// 存储桶表实体
/// </summary>
[Table("buckets")]
[Index(nameof(ServiceId), nameof(Name), IsUnique = true, Name = "IX_Buckets_ServiceId_Name_Unique")]
[Index(nameof(ServiceId), Name = "IX_Buckets_ServiceId")]
public class Bucket
{
    /// <summary>
    /// 主键，UUID
    /// </summary>
    [Key]
    [Comment("主键ID")]
    public Guid Id { get; set; }
    
    /// <summary>
    /// 存储桶名称（唯一）
    /// </summary>
    [Required]
    [MaxLength(100)]
    [Comment("存储桶名称")]
    public string Name { get; set; } = string.Empty;
    
    /// <summary>
    /// 存储桶描述
    /// </summary>
    [MaxLength(500)]
    [Comment("存储桶描述")]
    public string? Description { get; set; }
    
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
    /// 创建时间
    /// </summary>
    [Comment("创建时间")]
    public DateTimeOffset CreateTime { get; set; }
    
    /// <summary>
    /// 更新时间
    /// </summary>
    [Comment("更新时间")]
    public DateTimeOffset UpdateTime { get; set; }
    
    /// <summary>
    /// 是否启用
    /// </summary>
    [Comment("是否启用")]
    public bool IsEnabled { get; set; } = true;
    
    /// <summary>
    /// 关联的上传文件列表（一对多关系）
    /// </summary>
    public ICollection<UploadedFile> UploadedFiles { get; set; } = new List<UploadedFile>();
}
