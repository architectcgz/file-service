package com.architectcgz.file.application.service.filemanagement.query;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.application.dto.StorageStatistics;
import com.architectcgz.file.application.dto.TenantStorageStats;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileManagementQueryService {

    private final FileManagementRecordQueryService fileManagementRecordQueryService;
    private final FileManagementStatisticsQueryService fileManagementStatisticsQueryService;

    public PageResponse<FileRecord> listFiles(FileQuery query) {
        log.debug("Listing files with query: {}", query);
        return fileManagementRecordQueryService.listFiles(query);
    }

    public FileRecord getFileDetail(String fileId) {
        log.debug("Getting file detail for fileId: {}", fileId);
        return fileManagementRecordQueryService.findFileOrThrow(fileId);
    }

    public StorageStatistics getStorageStatistics() {
        log.debug("Getting storage statistics via SQL aggregation");
        return fileManagementStatisticsQueryService.getStorageStatistics();
    }

    public Map<String, TenantStorageStats> getStorageStatisticsByTenant() {
        log.debug("Getting storage statistics by tenant");
        return fileManagementStatisticsQueryService.getStorageStatisticsByTenant();
    }
}
