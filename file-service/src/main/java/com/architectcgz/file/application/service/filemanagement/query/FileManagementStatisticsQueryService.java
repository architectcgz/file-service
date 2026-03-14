package com.architectcgz.file.application.service.filemanagement.query;

import com.architectcgz.file.application.dto.StorageStatistics;
import com.architectcgz.file.application.dto.TenantStorageStats;
import com.architectcgz.file.domain.model.ContentTypeCount;
import com.architectcgz.file.domain.model.StorageStatisticsAggregation;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStorageAggregation;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文件管理统计查询服务。
 */
@Service
@RequiredArgsConstructor
public class FileManagementStatisticsQueryService {

    private final FileRecordRepository fileRecordRepository;
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;

    public StorageStatistics getStorageStatistics() {
        StorageStatisticsAggregation aggregation = fileRecordRepository.getStorageStatisticsAggregation(null);
        List<ContentTypeCount> typeCounts = fileRecordRepository.getFileCountByContentType(null);
        Map<String, Long> filesByType = typeCounts.stream()
                .collect(Collectors.toMap(ContentTypeCount::getContentType, ContentTypeCount::getFileCount));

        List<TenantStorageAggregation> tenantAggregations = fileRecordRepository.getStorageByTenant();
        Map<String, Long> storageByTenant = tenantAggregations.stream()
                .collect(Collectors.toMap(TenantStorageAggregation::getAppId, TenantStorageAggregation::getStorageBytes));

        return StorageStatistics.builder()
                .totalFiles(aggregation.getTotalFiles())
                .totalStorageBytes(aggregation.getTotalStorageBytes())
                .publicFiles(aggregation.getPublicFiles())
                .privateFiles(aggregation.getPrivateFiles())
                .filesByType(filesByType)
                .storageByTenant(storageByTenant)
                .statisticsTime(LocalDateTime.now())
                .build();
    }

    public Map<String, TenantStorageStats> getStorageStatisticsByTenant() {
        Map<String, TenantStorageStats> statsMap = new HashMap<>();
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            Optional<TenantUsage> usageOpt = tenantUsageRepository.findById(tenant.getTenantId());
            if (usageOpt.isEmpty()) {
                continue;
            }

            TenantUsage usage = usageOpt.get();
            double storageUsagePercent = tenant.getMaxStorageBytes() > 0
                    ? (double) usage.getUsedStorageBytes() / tenant.getMaxStorageBytes() * 100
                    : 0;
            double fileCountUsagePercent = tenant.getMaxFileCount() > 0
                    ? (double) usage.getUsedFileCount() / tenant.getMaxFileCount() * 100
                    : 0;

            statsMap.put(tenant.getTenantId(), TenantStorageStats.builder()
                    .tenantId(tenant.getTenantId())
                    .tenantName(tenant.getTenantName())
                    .fileCount(usage.getUsedFileCount())
                    .storageBytes(usage.getUsedStorageBytes())
                    .maxStorageBytes(tenant.getMaxStorageBytes())
                    .maxFileCount(tenant.getMaxFileCount())
                    .storageUsagePercent(storageUsagePercent)
                    .fileCountUsagePercent(fileCountUsagePercent)
                    .build());
        }

        return statsMap;
    }
}
