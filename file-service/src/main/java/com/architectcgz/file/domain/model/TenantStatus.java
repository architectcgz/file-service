package com.architectcgz.file.domain.model;

/**
 * 租户状态枚举
 */
public enum TenantStatus {
    /**
     * 活跃状态 - 租户可以正常使用服务
     */
    ACTIVE,

    /**
     * 停用状态 - 租户暂时无法使用服务
     */
    SUSPENDED,

    /**
     * 已删除状态 - 租户已被软删除
     */
    DELETED
}
