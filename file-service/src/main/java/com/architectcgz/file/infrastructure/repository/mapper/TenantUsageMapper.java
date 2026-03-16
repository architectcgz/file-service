package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.TenantUsagePO;
import org.apache.ibatis.annotations.*;

/**
 * 租户使用统计 MyBatis Mapper
 */
public interface TenantUsageMapper extends RuntimeMyBatisMapper {
    /**
     * 根据租户ID查找使用统计
     */
    @Select("""
        SELECT tenant_id, used_storage_bytes, used_file_count, last_upload_at, updated_at
        FROM tenant_usage
        WHERE tenant_id = #{tenantId}
    """)
    @Results(id = "tenantUsageResult", value = {
        @Result(property = "tenantId", column = "tenant_id"),
        @Result(property = "usedStorageBytes", column = "used_storage_bytes"),
        @Result(property = "usedFileCount", column = "used_file_count"),
        @Result(property = "lastUploadAt", column = "last_upload_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    TenantUsagePO findById(@Param("tenantId") String tenantId);

    /**
     * 插入使用统计
     */
    @Insert("""
        INSERT INTO tenant_usage (
            tenant_id, used_storage_bytes, used_file_count, last_upload_at, updated_at
        ) VALUES (
            #{tenantId}, #{usedStorageBytes}, #{usedFileCount},
            #{lastUploadAt},
            #{updatedAt}
        )
    """)
    void insert(TenantUsagePO usage);

    /**
     * 更新使用统计
     */
    @Update("""
        UPDATE tenant_usage
        SET used_storage_bytes = #{usedStorageBytes},
            used_file_count = #{usedFileCount},
            last_upload_at = #{lastUploadAt},
            updated_at = #{updatedAt}
        WHERE tenant_id = #{tenantId}
    """)
    void update(TenantUsagePO usage);

    /**
     * 原子性增加使用量
     */
    @Update("""
        UPDATE tenant_usage
        SET used_storage_bytes = used_storage_bytes + #{fileSize},
            used_file_count = used_file_count + 1,
            last_upload_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE tenant_id = #{tenantId}
    """)
    void incrementUsage(@Param("tenantId") String tenantId, @Param("fileSize") long fileSize);

    /**
     * 在租户仍处于活跃状态且更新后不超过配额时，原子性增加使用量。
     */
    @Update("""
        UPDATE tenant_usage tu
        SET used_storage_bytes = tu.used_storage_bytes + #{fileSize},
            used_file_count = tu.used_file_count + 1,
            last_upload_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        FROM tenants t
        WHERE tu.tenant_id = #{tenantId}
          AND t.tenant_id = tu.tenant_id
          AND t.status = 'active'
          AND tu.used_storage_bytes + #{fileSize} <= t.max_storage_bytes
          AND tu.used_file_count + 1 <= t.max_file_count
    """)
    int incrementUsageIfWithinQuota(@Param("tenantId") String tenantId, @Param("fileSize") long fileSize);

    /**
     * 原子性减少使用量
     */
    @Update("""
        UPDATE tenant_usage
        SET used_storage_bytes = GREATEST(0, used_storage_bytes - #{fileSize}),
            used_file_count = GREATEST(0, used_file_count - 1),
            updated_at = CURRENT_TIMESTAMP
        WHERE tenant_id = #{tenantId}
    """)
    void decrementUsage(@Param("tenantId") String tenantId, @Param("fileSize") long fileSize);
}
