package com.architectcgz.file.application.service.filemanagement.command;

import com.architectcgz.file.application.dto.BatchDeleteResult;
import com.architectcgz.file.application.service.filemanagement.audit.FileManagementAuditService;
import com.architectcgz.file.application.service.filemanagement.validator.FileManagementAdminValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAdminBatchDeleteCommandService {

    private final FileManagementAdminValidator fileManagementAdminValidator;
    private final FileManagementAuditService fileManagementAuditService;
    private final FileAdminDeleteCommandService fileAdminDeleteCommandService;

    public BatchDeleteResult batchDeleteFiles(List<String> fileIds, String adminUserId) {
        adminUserId = fileManagementAdminValidator.requireAdminUserId(adminUserId);
        log.info("Batch deleting {} files by admin: {}", fileIds.size(), adminUserId);

        BatchDeleteResult result = BatchDeleteResult.builder()
                .totalRequested(fileIds.size())
                .successCount(0)
                .failureCount(0)
                .failures(new java.util.ArrayList<>())
                .build();
        for (String fileId : fileIds) {
            try {
                fileAdminDeleteCommandService.deleteFile(fileId, adminUserId);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("Failed to delete file in batch: {}", fileId, e);
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailures().add(BatchDeleteResult.DeleteFailure.builder()
                        .fileId(fileId)
                        .reason(e.getMessage())
                        .build());
            }
        }

        fileManagementAuditService.recordBatchDeleteAudit(fileIds, result, adminUserId);
        log.info("Batch delete completed: {} succeeded, {} failed", result.getSuccessCount(), result.getFailureCount());
        return result;
    }
}
