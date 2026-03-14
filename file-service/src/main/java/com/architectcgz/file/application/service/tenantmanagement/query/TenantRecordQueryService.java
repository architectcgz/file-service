package com.architectcgz.file.application.service.tenantmanagement.query;

import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class TenantRecordQueryService {

    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public Tenant loadTenantOrThrow(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    public TenantUsage loadTenantUsageOrDefault(String tenantId) {
        return tenantUsageRepository.findById(tenantId)
                .orElse(new TenantUsage(tenantId));
    }

    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }
}
