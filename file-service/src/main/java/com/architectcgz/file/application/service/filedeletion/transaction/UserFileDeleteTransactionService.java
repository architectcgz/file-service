package com.architectcgz.file.application.service.filedeletion.transaction;

import com.architectcgz.file.application.service.filedeletion.accounting.FileDeletionUsageAccountingService;
import com.architectcgz.file.application.service.filedeletion.mutation.FileDeletionRecordMutationService;
import com.architectcgz.file.application.service.filedeletion.mutation.FileDeletionStorageReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFileDeleteTransactionService {

    private final FileDeletionRecordMutationService fileDeletionRecordMutationService;
    private final FileDeletionUsageAccountingService fileDeletionUsageAccountingService;
    private final FileDeletionStorageReleaseService fileDeletionStorageReleaseService;

    @Transactional(rollbackFor = Exception.class)
    public void commitUserDelete(String appId, String fileRecordId,
                                 String storageObjectId, Long fileSize) {
        fileDeletionRecordMutationService.markDeleted(fileRecordId);
        fileDeletionUsageAccountingService.decrementUsage(appId, fileSize);
        fileDeletionStorageReleaseService.release(storageObjectId);
    }
}
