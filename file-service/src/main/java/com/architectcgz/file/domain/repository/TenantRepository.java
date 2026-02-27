package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * 租户仓储接口
 */
public interface TenantRepository {
    /**
     * 根据ID查找租户
     */
    Optional<Tenant> findById(String tenantId);

    /**
     * 查询所有租户
     */
    List<Tenant> findAll();

    /**
     * 保存租户
     */
    Tenant save(Tenant tenant);

    /**
     * 删除租户
     */
    void delete(String tenantId);
}
