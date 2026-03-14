package com.architectcgz.file.application.service.filemanagement.audit;

import com.architectcgz.file.application.dto.BatchDeleteResult;
import com.architectcgz.file.application.service.AuditLogService;
import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.domain.model.AuditAction;
import com.architectcgz.file.domain.model.AuditLog;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件管理审计服务。
 */
@Service
@RequiredArgsConstructor
public class FileManagementAuditService {

    private final AuditLogService auditLogService;

    public void recordDeleteFileAudit(String fileId, FileRecord fileRecord, String adminUserId) {
        Map<String, Object> details = new HashMap<>();
        details.put("fileName", fileRecord.getOriginalFilename());
        details.put("fileSize", fileRecord.getFileSize());
        details.put("contentType", fileRecord.getContentType());
        details.put("storagePath", fileRecord.getStoragePath());

        AuditLog auditLog = AuditLog.builder()
                .adminUserId(adminUserId)
                .action(AuditAction.DELETE_FILE)
                .targetType(TargetType.FILE)
                .targetId(fileId)
                .tenantId(fileRecord.getAppId())
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();

        auditLogService.log(auditLog);
    }

    public void recordBatchDeleteAudit(List<String> fileIds, BatchDeleteResult result, String adminUserId) {
        Map<String, Object> details = new HashMap<>();
        details.put("totalRequested", result.getTotalRequested());
        details.put("successCount", result.getSuccessCount());
        details.put("failureCount", result.getFailureCount());
        details.put("fileIds", fileIds);

        AuditLog auditLog = AuditLog.builder()
                .adminUserId(adminUserId)
                .action(AuditAction.BATCH_DELETE_FILES)
                .targetType(TargetType.FILE)
                .targetId(String.format("batch_%d_files", fileIds.size()))
                .details(details)
                .ipAddress(AdminContext.getIpAddress())
                .build();

        auditLogService.log(auditLog);
    }
}
