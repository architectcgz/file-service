package com.architectcgz.file.application.service.tenantmanagement.query;

import com.architectcgz.file.domain.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantListQueryService {

    private final TenantRecordQueryService tenantRecordQueryService;

    public List<Tenant> listTenants() {
        return tenantRecordQueryService.listTenants();
    }
}
