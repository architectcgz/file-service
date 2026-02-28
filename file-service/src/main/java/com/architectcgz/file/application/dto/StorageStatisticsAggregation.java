package com.architectcgz.file.application.dto;

import lombok.Data;

/**
 * 存储统计聚合结果
 * 承载 SQL 层 COUNT/SUM 聚合查询的返回值，避免全量加载文件记录到内存
 */
@Data
public class StorageStatisticsAggregation {

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
}
