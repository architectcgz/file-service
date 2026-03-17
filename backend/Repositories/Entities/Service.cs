using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace FileService.Repositories.Entities;

/// <summary>
/// 服务表实体
/// </summary>
[Table("services")]
[Index(nameof(Name), IsUnique = true, Name = "IX_Services_Name_Unique")]
public class Service
{
    /// <summary>
    /// 主键，UUID
    /// </summary>
    [Key]
    [Comment("主键ID")]
    public Guid Id { get; set; }
    
    /// <summary>
    /// 服务名称（唯一）
    /// </summary>
    [Required]
    [MaxLength(100)]
    [Comment("服务名称")]
    public string Name { get; set; } = string.Empty;
    
    /// <summary>
    /// 服务描述
    /// </summary>
    [MaxLength(500)]
    [Comment("服务描述")]
    public string? Description { get; set; }
    
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
    /// 关联的存储桶列表（一对多关系）
    /// </summary>
    public ICollection<Bucket> Buckets { get; set; } = new List<Bucket>();
}
