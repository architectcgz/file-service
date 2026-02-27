package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.infrastructure.repository.mapper.TenantMapper;
import com.architectcgz.file.infrastructure.repository.po.TenantPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户仓储实现
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {
    private final TenantMapper tenantMapper;

    @Override
    public Optional<Tenant> findById(String tenantId) {
        TenantPO po = tenantMapper.findById(tenantId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return tenantMapper.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantPO po = toPO(tenant);
        TenantPO existing = tenantMapper.findById(tenant.getTenantId());
        
        if (existing == null) {
            tenantMapper.insert(po);
        } else {
            tenantMapper.update(po);
        }
        
        return tenant;
    }

    @Override
    public void delete(String tenantId) {
        tenantMapper.delete(tenantId);
    }

    private Tenant toDomain(TenantPO po) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(po.getTenantId());
        tenant.setTenantName(po.getTenantName());
        tenant.setStatus(TenantStatus.valueOf(po.getStatus().toUpperCase()));
        tenant.setMaxStorageBytes(po.getMaxStorageBytes());
        tenant.setMaxFileCount(po.getMaxFileCount());
        tenant.setMaxSingleFileSize(po.getMaxSingleFileSize());
        
        if (po.getAllowedFileTypes() != null) {
            tenant.setAllowedFileTypes(Arrays.asList(po.getAllowedFileTypes()));
        }
        
        tenant.setContactEmail(po.getContactEmail());
        tenant.setCreatedAt(po.getCreatedAt());
        tenant.setUpdatedAt(po.getUpdatedAt());
        return tenant;
    }

    private TenantPO toPO(Tenant tenant) {
        TenantPO po = new TenantPO();
        po.setTenantId(tenant.getTenantId());
        po.setTenantName(tenant.getTenantName());
        po.setStatus(tenant.getStatus().name().toLowerCase());
        po.setMaxStorageBytes(tenant.getMaxStorageBytes());
        po.setMaxFileCount(tenant.getMaxFileCount());
        po.setMaxSingleFileSize(tenant.getMaxSingleFileSize());
        
        if (tenant.getAllowedFileTypes() != null) {
            po.setAllowedFileTypes(tenant.getAllowedFileTypes().toArray(new String[0]));
        }
        
        po.setContactEmail(tenant.getContactEmail());
        po.setCreatedAt(tenant.getCreatedAt());
        po.setUpdatedAt(tenant.getUpdatedAt());
        return po;
    }
}
