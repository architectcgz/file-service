package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户存储统计响应对象
 * 包含单个租户的存储使用情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStorageStats {
    
    /**
     * 租户 ID
     */
    private String tenantId;
    
    /**
     * 租户名称
     */
    private String tenantName;
    
    /**
     * 文件数量
     */
    private long fileCount;
    
    /**
     * 已使用存储空间（字节）
     */
    private long storageBytes;
    
    /**
     * 最大存储空间（字节）
     */
    private long maxStorageBytes;
    
    /**
     * 最大文件数量
     */
    private long maxFileCount;
    
    /**
     * 存储空间使用百分比
     */
    private double storageUsagePercent;
    
    /**
     * 文件数量使用百分比
     */
    private double fileCountUsagePercent;
}
