package com.architectcgz.file.application.dto;

import lombok.Data;

/**
 * 按租户分组的存储聚合结果
 * 承载 SQL 层 GROUP BY app_id 聚合查询的返回值
 */
@Data
public class TenantStorageAggregation {

    /**
     * 租户ID（app_id）
     */
    private String appId;

    /**
     * 该租户的总存储空间（字节）
     */
    private long storageBytes;
}
