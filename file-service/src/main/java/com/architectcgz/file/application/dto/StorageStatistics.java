package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 存储统计响应对象
 * 包含全局存储使用情况的统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatistics {
    
    /**
     * 总文件数
     */
    private long totalFiles;
    
    /**
     * 总存储空间（字节）
     */
    private long totalStorageBytes;
    
    /**
     * 公开文件数量
     */
    private long publicFiles;
    
    /**
     * 私有文件数量
     */
    private long privateFiles;
    
    /**
     * 按文件类型分布（contentType -> 文件数量）
     */
    private Map<String, Long> filesByType;
    
    /**
     * 按租户存储空间分布（tenantId -> 存储字节数）
     */
    private Map<String, Long> storageByTenant;
    
    /**
     * 统计时间戳
     */
    private LocalDateTime statisticsTime;
}
