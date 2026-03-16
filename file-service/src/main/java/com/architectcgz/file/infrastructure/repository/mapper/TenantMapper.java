package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.TenantPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 租户 MyBatis Mapper
 */
public interface TenantMapper extends RuntimeMyBatisMapper {
    /**
     * 根据ID查找租户
     */
    @Select("""
        SELECT tenant_id, tenant_name, status, max_storage_bytes, max_file_count,
               max_single_file_size, allowed_file_types, contact_email, created_at, updated_at
        FROM tenants
        WHERE tenant_id = #{tenantId}
    """)
    @Results(id = "tenantResult", value = {
        @Result(property = "tenantId", column = "tenant_id"),
        @Result(property = "tenantName", column = "tenant_name"),
        @Result(property = "status", column = "status"),
        @Result(property = "maxStorageBytes", column = "max_storage_bytes"),
        @Result(property = "maxFileCount", column = "max_file_count"),
        @Result(property = "maxSingleFileSize", column = "max_single_file_size"),
        @Result(property = "allowedFileTypes", column = "allowed_file_types", 
                typeHandler = com.architectcgz.file.infrastructure.config.StringArrayTypeHandler.class),
        @Result(property = "contactEmail", column = "contact_email"),
        @Result(property = "createdAt", column = "created_at",
                typeHandler = com.architectcgz.file.infrastructure.config.LocalDateTimeTypeHandler.class),
        @Result(property = "updatedAt", column = "updated_at",
                typeHandler = com.architectcgz.file.infrastructure.config.LocalDateTimeTypeHandler.class)
    })
    TenantPO findById(@Param("tenantId") String tenantId);

    /**
     * 查询所有租户
     */
    @Select("""
        SELECT tenant_id, tenant_name, status, max_storage_bytes, max_file_count,
               max_single_file_size, allowed_file_types, contact_email, created_at, updated_at
        FROM tenants
        ORDER BY created_at DESC
    """)
    @ResultMap("tenantResult")
    List<TenantPO> findAll();

    /**
     * 插入租户
     */
    @Insert("""
        INSERT INTO tenants (
            tenant_id, tenant_name, status, max_storage_bytes, max_file_count,
            max_single_file_size, allowed_file_types, contact_email, created_at, updated_at
        ) VALUES (
            #{tenantId}, #{tenantName}, #{status}, #{maxStorageBytes}, #{maxFileCount},
            #{maxSingleFileSize}, #{allowedFileTypes,typeHandler=com.architectcgz.file.infrastructure.config.StringArrayTypeHandler}, 
            #{contactEmail}, 
            #{createdAt,typeHandler=com.architectcgz.file.infrastructure.config.LocalDateTimeTypeHandler}, 
            #{updatedAt,typeHandler=com.architectcgz.file.infrastructure.config.LocalDateTimeTypeHandler}
        )
    """)
    void insert(TenantPO tenant);

    /**
     * 更新租户
     */
    @Update("""
        UPDATE tenants
        SET tenant_name = #{tenantName},
            status = #{status},
            max_storage_bytes = #{maxStorageBytes},
            max_file_count = #{maxFileCount},
            max_single_file_size = #{maxSingleFileSize},
            allowed_file_types = #{allowedFileTypes,typeHandler=com.architectcgz.file.infrastructure.config.StringArrayTypeHandler},
            contact_email = #{contactEmail},
            updated_at = #{updatedAt,typeHandler=com.architectcgz.file.infrastructure.config.LocalDateTimeTypeHandler}
        WHERE tenant_id = #{tenantId}
    """)
    void update(TenantPO tenant);

    /**
     * 删除租户
     */
    @Delete("DELETE FROM tenants WHERE tenant_id = #{tenantId}")
    void delete(@Param("tenantId") String tenantId);
}
