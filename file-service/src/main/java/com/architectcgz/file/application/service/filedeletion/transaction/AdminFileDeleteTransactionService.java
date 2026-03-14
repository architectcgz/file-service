package com.architectcgz.file.application.service.filedeletion.transaction;

import com.architectcgz.file.application.service.filedeletion.accounting.FileDeletionUsageAccountingService;
import com.architectcgz.file.application.service.filedeletion.mutation.FileDeletionRecordMutationService;
import com.architectcgz.file.application.service.filedeletion.mutation.FileDeletionStorageReleaseService;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFileDeleteTransactionService {

    private final FileDeletionRecordMutationService fileDeletionRecordMutationService;
    private final FileDeletionUsageAccountingService fileDeletionUsageAccountingService;
    private final FileDeletionStorageReleaseService fileDeletionStorageReleaseService;

    @Transactional(rollbackFor = Exception.class)
    public void commitAdminDelete(String fileId, FileRecord fileRecord) {
        fileDeletionRecordMutationService.deleteOrThrow(fileId);
        fileDeletionUsageAccountingService.decrementUsage(fileRecord.getAppId(), fileRecord.getFileSize());
        fileDeletionStorageReleaseService.release(fileRecord.getStorageObjectId());
    }
}
