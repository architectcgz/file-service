package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.repository.mapper.TenantUsageMapper;
import com.architectcgz.file.infrastructure.repository.po.TenantUsagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 租户使用统计仓储实现
 * 使用数据库事务确保原子性更新
 */
@Repository
@RequiredArgsConstructor
public class TenantUsageRepositoryImpl implements TenantUsageRepository {
    private final TenantUsageMapper tenantUsageMapper;

    @Override
    public Optional<TenantUsage> findById(String tenantId) {
        TenantUsagePO po = tenantUsageMapper.findById(tenantId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    @Transactional
    public TenantUsage save(TenantUsage usage) {
        TenantUsagePO po = toPO(usage);
        TenantUsagePO existing = tenantUsageMapper.findById(usage.getTenantId());
        
        if (existing == null) {
            tenantUsageMapper.insert(po);
        } else {
            tenantUsageMapper.update(po);
        }
        
        return usage;
    }

    @Override
    @Transactional
    public void incrementUsage(String tenantId, long fileSize) {
        tenantUsageMapper.incrementUsage(tenantId, fileSize);
    }

    @Override
    @Transactional
    public boolean incrementUsageIfWithinQuota(String tenantId, long fileSize) {
        return tenantUsageMapper.incrementUsageIfWithinQuota(tenantId, fileSize) > 0;
    }

    @Override
    @Transactional
    public void decrementUsage(String tenantId, long fileSize) {
        tenantUsageMapper.decrementUsage(tenantId, fileSize);
    }

    private TenantUsage toDomain(TenantUsagePO po) {
        TenantUsage usage = new TenantUsage();
        usage.setTenantId(po.getTenantId());
        usage.setUsedStorageBytes(po.getUsedStorageBytes());
        usage.setUsedFileCount(po.getUsedFileCount());
        usage.setLastUploadAt(po.getLastUploadAt());
        usage.setUpdatedAt(po.getUpdatedAt());
        return usage;
    }

    private TenantUsagePO toPO(TenantUsage usage) {
        TenantUsagePO po = new TenantUsagePO();
        po.setTenantId(usage.getTenantId());
        po.setUsedStorageBytes(usage.getUsedStorageBytes());
        po.setUsedFileCount(usage.getUsedFileCount());
        po.setLastUploadAt(usage.getLastUploadAt());
        po.setUpdatedAt(usage.getUpdatedAt());
        return po;
    }
}
